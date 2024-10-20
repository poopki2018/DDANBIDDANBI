package poopki.org.dDANBI_1201;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import poopki.org.dDANBI_1201.DDANBI_1201;
import xyz.r2turntrue.chzzk4j.Chzzk;
import xyz.r2turntrue.chzzk4j.ChzzkBuilder;
import xyz.r2turntrue.chzzk4j.chat.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final DDANBI_1201 plugin;

    public PlayerJoinListener(DDANBI_1201 plugin) {
        this.plugin = plugin; // CHUNA 인스턴스 저장
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        plugin.loadPlayerSkipCount(playerUUID);
        // streamer_uuid.txt 파일에서 정보를 읽어옴
        File file = new File(plugin.getDataFolder(), "streamer_uuid.txt");
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length >= 3) {
                    String minecraftNickname = parts[0];
                    String broadcastUUID = parts[1];
                    String storedUUID = parts[2];

                    // UUID가 일치하는 경우
                    if (storedUUID.equals(playerUUID.toString())) {
                        // ChzzkChat 인스턴스 생성 및 연결
                        Chzzk chzzk = new ChzzkBuilder().build();
                        ChzzkChat chat = chzzk.chat(broadcastUUID)
                                .withChatListener(new ChatEventListener() {
                                    @Override
                                    public void onConnect(ChzzkChat chat, boolean isReconnecting) {
                                        System.out.println("Connect received!");
                                        if (!isReconnecting) {
                                            chat.requestRecentChat(0);
                                        }
                                    }

                                    @Override
                                    public void onError(Exception ex) {
                                        ex.printStackTrace();
                                    }

                                    @Override
                                    public void onChat(ChatMessage msg) {
                                        // 메시지 처리 로직
                                    }

                                    @Override
                                    public void onDonationChat(DonationMessage msg) {
                                        // UUID를 사용하여 플레이어 찾기
                                        plugin.handleDonationMessage(msg,player, UUID.fromString(storedUUID));
                                    }

                                    @Override
                                    public void onSubscriptionChat(SubscriptionMessage msg) {
                                        // 구독 처리 로직
                                    }
                                })
                                .build();

                        chat.connectBlocking();
                        plugin.userChats.put(playerUUID, chat); // 사용자 UUID에 해당하는 챗 인스턴스 저장

                        player.sendMessage(ChatColor.GREEN + "채팅 인스턴스가 연결되었습니다: " + minecraftNickname + " -> " + broadcastUUID);
                        break; // 해당 플레이어에 대한 처리 완료 후 루프 종료
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("파일 로드 중 오류 발생: " + e.getMessage());
        }
    }
}

