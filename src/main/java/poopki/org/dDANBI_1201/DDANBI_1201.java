package poopki.org.dDANBI_1201;

import org.bukkit.*;
import org.bukkit.entity.LightningStrike;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

import xyz.r2turntrue.chzzk4j.*;
import xyz.r2turntrue.chzzk4j.chat.*;

import java.io.*;

public final class DDANBI_1201 extends JavaPlugin implements Listener {

    Map<String, String> streamer_uuid = new HashMap<>();
    public Map<UUID, ChzzkChat> userChats = new HashMap<>(); // 사용자 UUID에 해당하는 챗 인스턴스 저장
    private long donationStartTime = 0; // 도네이션 메시지 수신 시작 시간
    private static final long DONATION_DELAY = 1000; // 1초 지연
    private final HashMap<UUID, Integer> playerSkipRandomRemovalCount = new HashMap<>();
    private Random random = new Random(System.nanoTime());
    Player lastTeleportedPlayer = null;

    @Override
    public void onEnable() {
        super.onEnable();
        loadStreamerData();
        PluginCommand command = getCommand("chzzk");
        getCommand("지급").setExecutor(this);

        if (command != null) {
            command.setExecutor(new RegisterCommand());
        } else {
            getLogger().severe("명령어 'chzzk'이 plugin.yml에 정의되어 있지 않습니다.");
        }
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // 모든 연결 종료
        for (ChzzkChat chat : userChats.values()) {
            chat.closeBlocking();
        }
    }
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        getLogger().severe("onPlayerDeath");
        Player player = event.getEntity();
        UUID playerUUID = player.getUniqueId();

        // playerSkipRandomRemovalCount가 1 이상인 경우
        getLogger().severe(String.valueOf(playerSkipRandomRemovalCount.get(playerUUID)));
        if (playerSkipRandomRemovalCount.get(playerUUID) > 0) {
            event.setKeepInventory(true);
            System.out.println("setKeepLevel-1");
            event.setKeepLevel(true);
            System.out.println("setKeepLevel-2");
            event.getDrops().clear();
            // playerSkipRandomRemovalCount 값 차감
            int currentCount = playerSkipRandomRemovalCount.get(playerUUID);
            if (currentCount > 0) {
                int remainingCount = currentCount - 1;
                playerSkipRandomRemovalCount.put(playerUUID, remainingCount); // 값 업데이트
                player.sendMessage(ChatColor.YELLOW + "아이템이 드롭되지 않았습니다. 남은 횟수: " + remainingCount);
                savePlayerSkipCount(playerUUID); // 플레이어에게 메시지 전송
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING || event.getCause() == EntityDamageEvent.DamageCause.CUSTOM) {
            if (event.getEntity() instanceof Player) {
                Player player = (Player) event.getEntity();
                event.setCancelled(true); // 기본 피해를 취소
                System.out.println("플레이어가 번개에 맞았습니다. 하트 2칸 감소.");
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("지급")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "플레이어만 이 명령어를 사용할 수 있습니다.");
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "사용법: /지급 \"마인크래프트닉네임\" \"숫자\"");
                return true;
            }

            String playerName = args[0].replace("\"", ""); // 큰따옴표 제거
            int value;

            try {
                value = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "숫자를 입력해 주세요.");
                return true;
            }

            // 입력한 플레이어 이름으로 플레이어 객체 찾기
            Player targetPlayer = Bukkit.getPlayer(playerName);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "해당 플레이어가 현재 온라인이 아닙니다.");
                return true;
            }

            // 특정 플레이어에게 효과 적용
            handleEffect(targetPlayer, value);
            return true;
        } else if (command.getName().equalsIgnoreCase("삭제")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "플레이어만 이 명령어를 사용할 수 있습니다.");
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "사용법: /삭제 \"마인크래프트닉네임\"");
                return true;
            }

            String playerName = args[0].replace("\"", ""); // 큰따옴표 제거

            // 플레이어 UUID 탐색
            String broadcastUUID = streamer_uuid.remove(playerName); // UUID 제거
            if (broadcastUUID == null) {
                sender.sendMessage(ChatColor.RED + "해당 플레이어의 데이터가 존재하지 않습니다.");
                return true;
            }

            // 파일에서 데이터 삭제
            removeFromFile(playerName);

            // 사용자별 ChzzkChat 인스턴스 제거
            UUID playerUUID = Bukkit.getPlayer(playerName).getUniqueId();
            userChats.remove(playerUUID);

            sender.sendMessage(ChatColor.GREEN + "삭제 완료: " + playerName);
            return true;
        }
        return false;
    }
    public void loadPlayerSkipCount(UUID uuid) {
        File file = new File(getDataFolder(), "counter.txt");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length == 2 && parts[0].equals(uuid.toString())) {
                    playerSkipRandomRemovalCount.put(uuid, Integer.parseInt(parts[1]));
                    return;
                }
            }
            // 해당 UUID가 없으면 0으로 초기화
            playerSkipRandomRemovalCount.put(uuid, 0);
            savePlayerSkipCount(uuid);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 파일에 데이터를 저장하는 메서드
    public void savePlayerSkipCount(UUID uuid) {
        File file = new File(getDataFolder(), "counter.txt");

        Map<UUID, Integer> currentCounts = new HashMap<>();

        // 기존 데이터를 불러옴
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ");
                    if (parts.length == 2) {
                        currentCounts.put(UUID.fromString(parts[0]), Integer.parseInt(parts[1]));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        // 현재 UUID의 값을 업데이트
        currentCounts.put(uuid, playerSkipRandomRemovalCount.get(uuid));

        // 데이터를 파일에 저장
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (Map.Entry<UUID, Integer> entry : currentCounts.entrySet()) {
                writer.write(entry.getKey().toString() + " " + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        // 번개에 의해 블록에 불이 붙는 것을 막음
        if (event.getCause() == BlockIgniteEvent.IgniteCause.LIGHTNING) {
            event.setCancelled(true);
        }
    }


    public void handleDonationMessage(DonationMessage msg,Player player, UUID uuid) {
        // UUID를 사용하여 플레이어 객체 찾기

        UUID playerUUID = player.getUniqueId();

        if (player != null) {
            // 기부 메시지 처리 로직
            handleEffect(player, msg.getPayAmount());
            System.out.println("[Donation] " + (msg.getProfile() != null ? msg.getProfile().getNickname() : "익명") + ": " + msg.getContent() + " [" + msg.getPayAmount() + "원]");
        } else {
            System.out.println("[Donation] 플레이어가 오프라인입니다: " + playerUUID);
        }
    }

    private class RegisterCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "사용법: /등록 \"마인크래프트닉네임\" \"방송UUID\"");
                return false;
            }

            String minecraftNickname = args[0].replace("\"", ""); // 닉네임에서 큰따옴표 제거
            String broadcastUUID = args[1].replace("\"", ""); // 방송 UUID

            // 서버에서 플레이어 UUID 탐색
            Player player = Bukkit.getPlayer(minecraftNickname);
            if (player == null) {
                sender.sendMessage(ChatColor.RED + "해당 플레이어가 온라인이 아닙니다.");
                return false;
            }

            UUID playerUUID = player.getUniqueId();
            if (!streamer_uuid.containsKey(minecraftNickname)) {
                streamer_uuid.put(minecraftNickname, broadcastUUID);
                saveToFile(minecraftNickname, broadcastUUID, playerUUID.toString());
            } else {
                // 중복된 닉네임 처리 로직 (예: 오류 메시지 출력)
                sender.sendMessage("이미 등록된 닉네임입니다: " + minecraftNickname);
                return false;
            }

            // 각 사용자별로 ChzzkChat 인스턴스 생성
            Chzzk chzzk = new ChzzkBuilder().build();
            ChzzkChat chat;
            try {
                chat = chzzk.chat(broadcastUUID) // 방송 UUID 사용
                        .withChatListener(new ChatEventListener() {

                            @Override
                            public void onConnect(ChzzkChat chat, boolean isReconnecting) {
                                System.out.println("Connect received!");
                                if (!isReconnecting) {
                                    donationStartTime = System.currentTimeMillis(); // 연결 시점 저장
                                    chat.requestRecentChat(0); // 최근 채팅 요청
                                }
                            }
                            @Override
                            public void onError(Exception ex) {
                                ex.printStackTrace();
                            }

                            @Override
                            public void onChat(ChatMessage msg) {

                            }

                            @Override
                            public void onDonationChat(DonationMessage msg) {

                                handleDonationMessage(msg,player, playerUUID);
                            }

                            @Override
                            public void onSubscriptionChat(SubscriptionMessage msg) {

                            }
                        })
                        .build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            chat.connectBlocking();
            userChats.put(playerUUID, chat); // 사용자 UUID에 해당하는 챗 인스턴스 저장

            sender.sendMessage(ChatColor.GREEN + "등록 완료: " + minecraftNickname + " -> " + broadcastUUID);
            return true;
        }

        private void handleChatMessage(ChatMessage msg) {
            System.out.println("[Chat] " + (msg.getProfile() != null ? msg.getProfile().getNickname() : "익명") + ": " + msg.getContent());
        }


        private void handleSubscriptionMessage(SubscriptionMessage msg) {
            // 구독 메시지 처리 로직
            System.out.println("[Subscription] " + (msg.getProfile() != null ? msg.getProfile().getNickname() : "익명") + ": " + msg.getContent());
        }

        private void saveToFile(String nickname, String broadcastUUID, String playerUUID) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(getDataFolder() + "/streamer_uuid.txt", true))) {
                writer.write(nickname + " " + broadcastUUID + " " + playerUUID);
                writer.newLine();
                getLogger().severe("파일 저장 중...");
            } catch (IOException e) {
                getLogger().severe("파일 저장 중 오류 발생: " + e.getMessage());
            }
        }
    }
    private void removeFromFile(String nickname) {
        File file = new File(getDataFolder(), "streamer_uuid.txt");
        File tempFile = new File(getDataFolder(), "temp_streamer_uuid.txt");

        try (BufferedReader reader = new BufferedReader(new FileReader(file));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (!parts[0].equals(nickname)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            getLogger().severe("파일 삭제 중 오류 발생: " + e.getMessage());
        }

        // 기존 파일 삭제하고 임시 파일을 원래 파일 이름으로 변경
        file.delete();
        tempFile.renameTo(file);
    }
    private void loadStreamerData() {
        File file = new File(getDataFolder(), "streamer_uuid.txt");
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                getLogger().severe("UUID LOAD "+line);
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    String nickname = parts[0];
                    String broadcastUUID = parts[1];
                    streamer_uuid.put(nickname, broadcastUUID);
                }
            }
        } catch (IOException e) {
            getLogger().severe("파일 로드 중 오류 발생: " + e.getMessage());
        }
    }
    public void removeRandomItemFromInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<Integer> removableSlots = new ArrayList<>();

        // 인벤토리의 모든 아이템을 순회
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);

            // 손에 들고 있는 아이템이나 장착하고 있는 아이템이 아닐 경우 추가
            if (item != null && !item.equals(inventory.getItemInMainHand()) && !isInArmorContents(item, inventory.getArmorContents())) {
                removableSlots.add(i);
            }
        }

        // 삭제할 아이템이 있는 경우
        if (!removableSlots.isEmpty()) {
            int randomIndex = random.nextInt(removableSlots.size());
            int slotToRemove = removableSlots.get(randomIndex);

            // 랜덤하게 선택된 아이템 삭제
            inventory.clear(slotToRemove);
        }
    }

    // 장착하고 있는 아이템인지 확인하는 메서드
    private boolean isInArmorContents(ItemStack item, ItemStack[] armorContents) {
        for (ItemStack armor : armorContents) {
            if (armor != null && armor.isSimilar(item)) {
                return true;
            }
        }
        return false;
    }

    private void handleEffect(Player player, int value) {
        World world = Bukkit.getWorlds().get(0); // 기본 월드를 가져옴'
        UUID playerUUID = player.getUniqueId(); // 플레이어 UUID
        Location playerLocation = player.getLocation();
        switch (value) {
            case 3000:
                getLogger().info("3000");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendTitle(
                        ChatColor.WHITE + "3000원 후원",  // Title in white
                        ChatColor.YELLOW + "안익은 고기 5개",  // Subtitle in yellow
                        10,  // Fade-in time (ticks)
                        70,  // Stay time (ticks)
                        20   // Fade-out time (ticks)
                );
                player.getInventory().addItem(new ItemStack(Material.BEEF, 5));
                break;

            case 5000:
                // ???? 효과 추가 필요
                getLogger().info("5000");
// Play sound and display title
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendTitle(
                        ChatColor.WHITE + "5000원 후원",  // Title in white
                        ChatColor.YELLOW + "몬스터 3마리 소환",  // Subtitle in yellow
                        10,  // Fade-in time (ticks)
                        70,  // Stay time (ticks)
                        20   // Fade-out time (ticks)
                );

                playerLocation = player.getLocation();
                int openSpaces = 0;
                int checkRadius = 10;

// 주변 5칸 내에 개방된 공간 확인
                for (int x = -checkRadius; x <= checkRadius; x++) {
                    for (int z = -checkRadius; z <= checkRadius; z++) {
                        Location checkLocation = playerLocation.clone().add(x, 0, z);
                        if (world.getBlockAt(checkLocation).isPassable()) {
                            openSpaces++;
                        }
                    }
                }

// 평지 여부 판단: 5x5 반경에 개방된 공간이 충분한 경우를 기준으로 설정
                boolean isFlat = openSpaces > (checkRadius * checkRadius * 0.5); // 50% 이상의 공간이 개방된 경우를 평지로 간주

// 소환할 오프셋 범위 결정
                double offsetRange = isFlat ? 10 : 1; // 평지에서는 최대 10, 좁은 공간에서는 최대 3으로 설정

                int spawnedCount = 0;
                int attempts = 0;
                int maxAttempts = 100; // 최대 시도 횟수

                while (spawnedCount < 3 && attempts < maxAttempts) {
                    double xOffset = (random.nextDouble() - 0.5) * offsetRange;
                    double zOffset = (random.nextDouble() - 0.5) * offsetRange;
                    Location spawnLocation = playerLocation.clone().add(xOffset, 0, zOffset);

                    // 플레이어의 Y 좌표에 맞춰 Y 좌표 설정
                    spawnLocation.setY(playerLocation.getY() + 1); // +1을 추가하여 플레이어 위에 스폰

                    // 공기 블록 확인: 스폰 위치와 그 위의 블록이 모두 공기인지 확인
                    if (world.getBlockAt(spawnLocation).getType() == Material.AIR &&
                            world.getBlockAt(spawnLocation.clone().add(0, 1, 0)).getType() == Material.AIR) {

                        EntityType entityType;
                        int randomValue = random.nextInt(100); // 0부터 99까지의 랜덤 숫자 생성
                        System.out.println(randomValue);
                        if (randomValue < 95) { // 95% 확률
                            // 일반 몬스터 랜덤 선택
                            int monsterType = random.nextInt(6); // 0부터 5까지의 랜덤 숫자
                            switch (monsterType) {
                                case 0:
                                    entityType = EntityType.ZOMBIE;
                                    break;
                                case 1:
                                    entityType = EntityType.SKELETON;
                                    break;
                                case 2:
                                    entityType = EntityType.CREEPER;
                                    break;
                                case 3:
                                    entityType = EntityType.SPIDER;
                                    break;
                                case 4:
                                    entityType = EntityType.BLAZE;
                                    break;
                                case 5:
                                    entityType = EntityType.SLIME;
                                    break;
                                default:
                                    entityType = EntityType.ZOMBIE; // 기본값
                            }
                        } else { // 5% 확률
                            // 희귀 몬스터 랜덤 선택
                            int rareMonsterType = random.nextInt(3); // 0부터 2까지의 랜덤 숫자
                            switch (rareMonsterType) {
                                case 0:
                                    entityType = EntityType.WARDEN; // 워든
                                    break;
                                case 1:
                                    entityType = EntityType.BLAZE; // 블레이즈
                                    break;
                                case 2:
                                    entityType = EntityType.CAVE_SPIDER; // 동굴거미
                                    break;
                                default:
                                    entityType = EntityType.ZOMBIE; // 기본값
                            }
                        }

                        // 몬스터 스폰
                        world.spawnEntity(spawnLocation, entityType);
                        spawnedCount++;
                    }

                    attempts++;
                }


// 모든 시도가 끝났을 때, 여전히 3마리를 소환하지 못했다면 추가로 처리할 수 있음
                if (spawnedCount < 3) {
                    getLogger().warning("충분한 공간을 찾지 못해 몬스터를 " + spawnedCount + "마리만 소환했습니다.");
                }

                break;

            case 10000:

                // 감옥 수감 (2분 고정)
                getLogger().info("10000");
                // Play sound and teleport to jail
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendTitle(
                        ChatColor.WHITE + "10000원 후원",  // Title in white
                        ChatColor.YELLOW + "꽈광~",  // Subtitle in yellow
                        10, 70, 20
                );


                // 메인 스레드에서 번개를 생성하도록 예약
                Location finalPlayerLocation1 = playerLocation;
                Bukkit.getScheduler().runTask(this, () -> {
                    // 플레이어의 위치에 번개를 생성합니다.
                    world.strikeLightning(finalPlayerLocation1);

                    // 하트 2개(4 체력)만큼 피해를 입힙니다.
                    double newHealth = Math.max(0, player.getHealth() / 2); // 2 하트 = 4 체력
                    player.setHealth(newHealth);
                });
            break;
            case 20000:
                // 감옥 출소
                getLogger().info("20000");
// Play sound and teleport to jail
                player.sendTitle(
                        ChatColor.WHITE + "20000원 후원",  // Title in white
                        ChatColor.YELLOW + "랜덤 텔포",  // Subtitle in yellow
                        10,  // Fade-in time (ticks)
                        70,  // Stay time (ticks)
                        20   // Fade-out time (ticks)
                );

// 자기 자신을 제외한 랜덤 플레이어 선택
                List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
                onlinePlayers.remove(player); // 자기 자신 제거

                if (!onlinePlayers.isEmpty()) {
                    Player randomPlayer;

                    do {
                        randomPlayer = onlinePlayers.get(random.nextInt(onlinePlayers.size()));
                    } while (randomPlayer.equals(lastTeleportedPlayer)); // 직전 텔레포트된 플레이어 제외

                    // 메인 스레드에서 텔레포트 실행
                    Player finalRandomPlayer = randomPlayer;
                    Bukkit.getScheduler().runTask(this, () -> {
                        player.teleport(finalRandomPlayer.getLocation());
                        player.sendMessage(ChatColor.GREEN + finalRandomPlayer.getName() + "의 위치로 텔레포트되었습니다.");

                        // 선택된 플레이어를 이전 플레이어 변수에 저장
                        lastTeleportedPlayer = finalRandomPlayer;
                    });
                } else {
                    player.sendMessage(ChatColor.RED + "온라인 플레이어가 없습니다.");
                }

                break;

            case 30000:
                // 랜덤박스
                getLogger().info("30000");
                // 예시로 랜덤 장비 지급
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendTitle(
                        ChatColor.WHITE + "30000원 후원",  // Title in white
                        ChatColor.YELLOW + "어흑마이깟~",  // Subtitle in yellow
                        10, 70, 20
                );
                removeRandomItemFromInventory(player);

                break;

            case 50000:
                playerSkipRandomRemovalCount.putIfAbsent(playerUUID, 0); // 플레이어 UUID가 없으면 초기값 0으로 설정
                playerSkipRandomRemovalCount.put(playerUUID, playerSkipRandomRemovalCount.getOrDefault(playerUUID, 0) + 1);
                int remainingCount = playerSkipRandomRemovalCount.get(playerUUID);
                savePlayerSkipCount(playerUUID);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendTitle(
                        ChatColor.WHITE + "50000원 후원",  // Title in white
                        ChatColor.YELLOW + "인벤 방지권 / 남은 횟수:" + remainingCount,  // Subtitle in yellow
                        10,  // Fade-in time (ticks)
                        70,  // Stay time (ticks)
                        20   // Fade-out time (ticks)
                );
                getLogger().info("50000");
                break;

            case 100000:
                // 해당 스트리머 캐릭터 즉사
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendTitle(
                        ChatColor.WHITE + "100000원 후원",  // Title in white
                        ChatColor.YELLOW + "즉사",  // Subtitle in yellow
                        10,  // Fade-in time (ticks)
                        70,  // Stay time (ticks)
                        20   // Fade-out time (ticks)
                );
                if (player != null) {
                    player.setHealth(0); // 플레이어의 체력을 0으로 설정하여 즉사시킴
                }

                break;
            default:
                getLogger().warning("알 수 없는 값 수신: " + value);
        }
    }
}
