package facebook.common;

public final class Checks {
    private Checks() {
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public static String text(String value) {
        require(value != null && !value.isBlank(), "Text must not be blank");
        return value.trim();
    }
}
