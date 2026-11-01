package io.github.op7418.guizangtv;

/** Keeps the whole clock inside the display while drifting across it. */
final class ClockLayout {
    static float[] position(float width, float height, float contentWidth, float contentHeight,
                            float padding, long elapsed) {
        float spaceX = Math.max(0, width - contentWidth - 2 * padding);
        float spaceY = Math.max(0, height - contentHeight - 2 * padding);
        double phase = elapsed / 1200000.0 * 2 * Math.PI;
        float x = Math.min(padding, Math.max(0, (width - contentWidth) / 2))
                + spaceX * (float) (.5 + .48 * Math.sin(phase));
        float y = Math.min(padding, Math.max(0, (height - contentHeight) / 2))
                + spaceY * (float) (.5 + .48 * Math.cos(phase * .73));
        return new float[]{x, y};
    }
}
