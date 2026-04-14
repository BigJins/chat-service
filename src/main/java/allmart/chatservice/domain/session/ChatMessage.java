package allmart.chatservice.domain.session;

/**
 * 단일 대화 메시지.
 * role: "user" | "assistant"
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
