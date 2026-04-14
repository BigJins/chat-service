package allmart.chatservice.domain.session;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * buyerId → ChatSession 인메모리 저장소.
 * 마지막 접근 기준 30분 비활성 시 자동 만료 (Guava Cache expireAfterAccess).
 */
@Component
public class ChatSessionStore {

    private final Cache<Long, ChatSession> store = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    public ChatSession getOrCreate(Long buyerId) {
        try {
            return store.get(buyerId, () -> new ChatSession(buyerId));
        } catch (ExecutionException e) {
            // 생성 실패는 실질적으로 불가능 — 새 ChatSession() 예외 없음
            ChatSession session = new ChatSession(buyerId);
            store.put(buyerId, session);
            return session;
        }
    }

    public void remove(Long buyerId) {
        store.invalidate(buyerId);
    }
}
