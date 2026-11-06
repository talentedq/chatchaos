package ru.chatchaos;

/** Категория эффекта: влияет на цвет в HUD и на баланс в голосовании. */
public enum Cat {
    GOOD("Добро", 0xFF55FF55),
    BAD("Беда", 0xFFFF5555),
    FUN("Веселье", 0xFFFFD34D);

    public final String label;
    public final int color;

    Cat(String label, int color) {
        this.label = label;
        this.color = color;
    }
}
