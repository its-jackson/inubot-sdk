package com.itsjackson.script.sdk.paint;

import org.rspeer.game.component.tdi.Skill;
import org.rspeer.game.script.meta.paint.PaintScheme;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

public class ZeroFluxPaintScheme extends PaintScheme {
    private static final Color BACKGROUND = new Color(20, 20, 20); // Near-black background
    private static final Color BORDER = new Color(50, 50, 50); // Dark grey border
    private static final Color FONT = new Color(220, 220, 220); // Bright white-grey for text readability
    private static final Color TITLE = new Color(255, 255, 255); // Pure white for titles
    private static final Color SHOW_BTN = new Color(60, 60, 60); // Dark grey for show button
    private static final Color HIDE_BTN = new Color(40, 40, 40); // Slightly darker grey for hide button
    private static final Color HOVER_STATS_BOX_BACKGROUND = new Color(30, 30, 30, 220); // Semi-transparent near-black for hover
    private static final Color DEFAULT_SKILL_COLOR = new Color(180, 180, 180); // Light grey for skills

    private static final Font TITLE_FONT = new Font("Roboto", Font.BOLD, 14);
    private static final Font BODY_FONT = new Font("Roboto", Font.PLAIN, 12);

    public static final String FOOTER = "ZeroFlux Paint";

    private static final Map<Skill, Color> SKILL_COLORS = new EnumMap<>(Skill.class);

    static {
        for (Skill skill : Skill.values()) {
            SKILL_COLORS.put(skill, DEFAULT_SKILL_COLOR);
        }
    }

    @Override
    public Color getBackgroundColor() {
        return BACKGROUND;
    }

    @Override
    public Color getBorderColor() {
        return BORDER;
    }

    @Override
    public Color getFontColor() {
        return FONT;
    }

    @Override
    public Color getTitleColor() {
        return TITLE;
    }

    @Override
    public Color getShowColor() {
        return SHOW_BTN;
    }

    @Override
    public Color getHideColor() {
        return HIDE_BTN;
    }

    @Override
    public Color getSkillHoverBoxBackground() {
        return HOVER_STATS_BOX_BACKGROUND;
    }

    @Override
    public Color getSkillColor(Skill skill) {
        return SKILL_COLORS.getOrDefault(skill, DEFAULT_SKILL_COLOR);
    }

    @Override
    public Font getTitleFont() {
        return TITLE_FONT;
    }

    @Override
    public Font getBodyFont() {
        return BODY_FONT;
    }

    @Override
    public int getBackgroundAlpha() {
        return 240; // Slightly less transparent for a bolder, darker look
    }

    @Override
    public int getX() {
        return 10; // Consistent with a clean layout
    }

    @Override
    public int getY() {
        return 10; // Positioned near top-left for a modern UI
    }

    @Override
    public int getPadding() {
        return 6; // Tighter padding for an even sleeker, more compact look
    }

    @Override
    public int getArcLength() {
        return 6; // Slightly more rounded corners for a smoother, modern feel
    }

    @Override
    public String getFooter() {
        return FOOTER; // Updated footer for the new version
    }

    @Override
    public Image getIcon() {
        return null;
        // return super.getIcon();
    }
}