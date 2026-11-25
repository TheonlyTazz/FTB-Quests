package dev.ftb.mods.ftbquests.client.gui.quests;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftblibrary.ui.input.KeyModifiers;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Owns the non-modal quest windows shown above the quest graph. */
public class QuestWindowWorkspace extends Panel {
    private static final int CASCADE_OFFSET = 18;
    private static final int OUTSIDE = -100000;

    private final QuestScreen questScreen;
    private final List<ViewQuestPanel> windows = new ArrayList<>();
    @Nullable private ViewQuestPanel focusedWindow;
    @Nullable private ViewQuestPanel pressedWindow;

    public QuestWindowWorkspace(QuestScreen questScreen) {
        super(questScreen);
        this.questScreen = questScreen;
        setOnlyRenderWidgetsInside(false);
        setOnlyInteractWithWidgetsInside(false);
    }

    @Override public void addWidgets() {
        setSize(questScreen.width, questScreen.height);
        windows.forEach(this::add);
    }
    @Override public void alignWidgets() {
        setSize(questScreen.width, questScreen.height);
    }

    public ViewQuestPanel openQuest(Quest quest, boolean additional) {
        ViewQuestPanel window = windows.stream()
                .filter(candidate -> candidate.getViewedQuest() != null && candidate.getViewedQuest().id == quest.id)
                .findFirst().orElse(null);
        if (window == null) {
            window = new ViewQuestPanel(questScreen, this);
            windows.add(window);
            add(window);
            window.setViewedQuest(quest);
            ViewQuestPanel previous = focusedWindow;
            if (previous != null && previous.getViewedQuest() != null) {
                window.setWindowPosition(previous.posX + CASCADE_OFFSET, previous.posY + CASCADE_OFFSET);
            }
        }
        if (!additional) closeUnkeptWindowsExcept(window);
        focus(window);
        updateMouseOver(getMouseX(), getMouseY());
        return window;
    }

    public void closeWindow(ViewQuestPanel window) {
        if (!windows.remove(window)) return;
        widgets.remove(window);
        if (pressedWindow == window) pressedWindow = null;
        if (focusedWindow == window) focusedWindow = windows.isEmpty() ? null : windows.get(windows.size() - 1);
        window.onClosed();
        updateMouseOver(getMouseX(), getMouseY());
    }

    public void closeUnkeptWindows() {
        for (ViewQuestPanel window : List.copyOf(windows)) if (!window.isKeptOpen()) closeWindow(window);
    }

    private void closeUnkeptWindowsExcept(ViewQuestPanel except) {
        for (ViewQuestPanel window : List.copyOf(windows)) {
            if (window != except && !window.isKeptOpen()) closeWindow(window);
        }
    }

    @Nullable public ViewQuestPanel getFocusedWindow() { return focusedWindow; }
    public List<ViewQuestPanel> getWindows() { return List.copyOf(windows); }

    /** Highest translation needed for this workspace, including quest item overlays. */
    public int getMaxZLevel() {
        return windows.isEmpty() ? 0 : 1250 + (windows.size() - 1) * 250 + 200;
    }

    @Override public void onClosed() {
        if (pressedWindow != null && windows.contains(pressedWindow)) pressedWindow.mouseReleased(MouseButton.LEFT);
        pressedWindow = null;
    }

    private void focus(ViewQuestPanel window) {
        if (!windows.contains(window)) return;
        focusedWindow = window;
        windows.remove(window);
        windows.add(window);
        widgets.remove(window);
        widgets.add(window);
    }

    @Nullable private ViewQuestPanel windowAt(int mouseX, int mouseY) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            ViewQuestPanel window = windows.get(i);
            if (window.checkMouseOver(mouseX, mouseY)) return window;
        }
        return null;
    }

    @Override public boolean checkMouseOver(int mouseX, int mouseY) { return windowAt(mouseX, mouseY) != null; }

    @Override public void updateMouseOver(int mouseX, int mouseY) {
        ViewQuestPanel hit = windowAt(mouseX, mouseY);
        isMouseOver = hit != null;
        for (ViewQuestPanel window : List.copyOf(windows)) {
            window.updateMouseOver(window == hit ? mouseX : OUTSIDE, window == hit ? mouseY : OUTSIDE);
        }
    }

    @Override public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        int z = 1250;
        for (ViewQuestPanel window : List.copyOf(windows)) {
            if (window.getViewedQuest() != null) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, z);
                window.draw(graphics, theme, window.getX(), window.getY(), window.width, window.height);
                graphics.pose().popPose();
                z += 250;
            }
        }
        if (questScreen.hasQuestModalOverlay() && !windows.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, getMaxZLevel() + 100);
            Color4I.rgba(0xA0202020).draw(graphics, getX(), getY(), width, height);
            graphics.pose().popPose();
        }
    }

    @Override public boolean mousePressed(MouseButton button) {
        ViewQuestPanel window = windowAt(getMouseX(), getMouseY());
        if (window == null) return false;
        focus(window);
        pressedWindow = window;
        window.mousePressed(button);
        return true;
    }

    @Override public boolean mouseDragged(int button, double dragX, double dragY) {
        return pressedWindow != null && windows.contains(pressedWindow) && pressedWindow.mouseDragged(button, dragX, dragY);
    }

    @Override public void mouseReleased(MouseButton button) {
        ViewQuestPanel window = pressedWindow;
        pressedWindow = null;
        if (window != null && windows.contains(window)) window.mouseReleased(button);
    }

    @Override public boolean mouseDoubleClicked(MouseButton button) {
        ViewQuestPanel window = windowAt(getMouseX(), getMouseY());
        if (window == null) return false;
        focus(window);
        return window.mouseDoubleClicked(button);
    }

    @Override public boolean mouseScrolled(double scroll) {
        ViewQuestPanel window = windowAt(getMouseX(), getMouseY());
        if (window == null) return false;
        window.mouseScrolled(scroll);
        return true;
    }

    @Override public boolean keyPressed(Key key) {
        return focusedWindow != null && windows.contains(focusedWindow) && focusedWindow.keyPressed(key);
    }

    @Override public void keyReleased(Key key) {
        if (focusedWindow != null && windows.contains(focusedWindow)) focusedWindow.keyReleased(key);
    }

    @Override public boolean charTyped(char codePoint, KeyModifiers modifiers) {
        return focusedWindow != null && windows.contains(focusedWindow) && focusedWindow.charTyped(codePoint, modifiers);
    }
}
