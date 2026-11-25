package dev.ftb.mods.ftbquests.client.gui.quests;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.BlankPanel;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.theme.property.ThemeProperties;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** A companion window whose task buttons navigate to prerequisites rather than submitting them. */
public class QuestDependenciesPanel extends Panel {
	private static final int MAX_HEIGHT = 220;
	private static final int TITLE_ICON_SIZE = 10;
	private static final int TASK_SPACING = 22;
	private final ViewQuestPanel owner;
	private final QuestScreen questScreen;
	private final Quest quest;
	private final List<QuestObject> dependencies;
	private List<DependencyState> dependencyStates = List.of();
	private int requestedWidth;
	private int requestedHeight;

	public QuestDependenciesPanel(ViewQuestPanel owner, QuestScreen questScreen, Quest quest, List<QuestObject> dependencies) {
		super(owner);
		this.owner = owner;
		this.questScreen = questScreen;
		this.quest = quest;
		this.dependencies = List.copyOf(dependencies);
	}

	public void layout(int width, int maxHeight) {
		requestedWidth = width;
		requestedHeight = Math.min(MAX_HEIGHT, maxHeight * 2 / 3);
		refreshWidgets();
	}

	@Override
	public void addWidgets() {
		setSize(requestedWidth, requestedHeight);
		TextField heading = new TextField(this).setMaxWidth(width - 28)
				.setText(Component.translatable("ftbquests.gui.view_dependencies"));
		heading.setPos(5, 5);
		heading.setColor(ThemeProperties.QUEST_VIEW_TITLE.get(quest));
		add(heading);
		SimpleButton close = new SimpleButton(this, Component.translatable("gui.close"), ThemeProperties.CLOSE_ICON.get(quest),
				(button, mouseButton) -> {
					if (mouseButton.isLeft()) {
						owner.toggleDependenciesPanel();
					}
				});
		close.setPosAndSize(width - 18, 3, 14, 14);
		add(close);

		Component requirement = Component.translatable("ftbquests.quest.dependency_requirement." + quest.getDependencyRequirement().getId());
		if (quest.getMinRequiredDependencies() > 0) {
			requirement = Component.translatable("ftbquests.quest.min_required_header", quest.getMinRequiredDependencies());
		}
		TextField requirementField = new TextField(this).setMaxWidth(width - 10).setText(requirement);
		requirementField.setColor(Color4I.GRAY);
		requirementField.setPos(5, heading.posY + heading.height + 5);
		add(requirementField);
		int contentTop = requirementField.posY + requirementField.height + 5;

		BlankPanel content = new BlankPanel(this);
		content.setPosAndSize(3, contentTop, width - 14, 1);
		add(content);
		dependencyStates = buildDependencyStates();
		int y = 0;
		int hidden = 0;
		for (DependencyState state : dependencyStates) {
			if (!state.visible()) {
				hidden++;
				continue;
			}
			DependencyCard card = new DependencyCard(content, state);
			card.setPos(0, y);
			content.add(card);
			y += card.height + 5;
		}
		if (hidden > 0) {
			TextField hiddenField = new TextField(content).setMaxWidth(content.width - 4)
					.setText(Component.translatable("ftbquests.quest.hidden_quests_footer", hidden));
			hiddenField.setColor(Color4I.GRAY);
			hiddenField.setPos(2, y);
			content.add(hiddenField);
			y += hiddenField.height + 3;
		}
		setHeight(Math.min(requestedHeight, contentTop + y + 4));
		content.setHeight(Math.max(1, height - contentTop - 4));
		if (y > content.height) {
			PanelScrollBar scrollbar = new PanelScrollBar(this, content);
			scrollbar.setPosAndSize(width - 9, contentTop, 6, content.height);
			add(scrollbar);
		}
	}

	private boolean canInspectQuest(Quest dependency) {
		return questScreen.file.canEdit() || !dependency.hideDetailsUntilStartable()
				|| questScreen.file.selfTeamData.canStartTasks(dependency);
	}

	private List<Task> getTasks(QuestObject dependency) {
		if (dependency instanceof Quest dependencyQuest && canInspectQuest(dependencyQuest)) {
			return List.copyOf(dependencyQuest.getTasks());
		} else if (dependency instanceof Task task && canInspectQuest(task.getQuest())) {
			return List.of(task);
		} else if (dependency instanceof Chapter chapter) {
			return chapter.getQuests().stream()
					.filter(q -> questScreen.file.canEdit() || q.isSearchable(questScreen.file.selfTeamData))
					.filter(this::canInspectQuest)
					.flatMap(q -> q.getTasks().stream()).toList();
		}
		return List.of();
	}

	private List<DependencyState> buildDependencyStates() {
		return dependencies.stream().map(dependency -> {
			boolean visible = questScreen.file.canEdit() || dependency.isSearchable(questScreen.file.selfTeamData);
			boolean navigable = !(dependency instanceof Quest q) || canInspectQuest(q);
			if (dependency instanceof Task task) {
				navigable = canInspectQuest(task.getQuest());
				visible = questScreen.file.canEdit() || navigable && task.getQuest().isSearchable(questScreen.file.selfTeamData);
			}
			return new DependencyState(dependency, visible, navigable, visible ? getTasks(dependency) : List.of());
		}).toList();
	}

	public boolean needsRefresh() {
		return !dependencyStates.equals(buildDependencyStates());
	}

	@Override
	public void alignWidgets() {
	}

	@Override
	public boolean scrollPanel(double scroll) {
		return false;
	}

	@Override
	public boolean mousePressed(MouseButton button) {
		return super.mousePressed(button) || isMouseOver();
	}

	@Override
	public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
		ThemeProperties.QUEST_VIEW_BACKGROUND.get(quest).draw(graphics, x, y, w, h);
		GuiHelper.drawHollowRect(graphics, x, y, w, h, ThemeProperties.QUEST_VIEW_BORDER.get(quest), false);
	}

	private record DependencyState(QuestObject dependency, boolean visible, boolean navigable, List<Task> tasks) {
	}

	private final class DependencyCard extends Panel {
		private final DependencyState state;
		private final TextField title;
		private final int headerHeight;
		private final int taskTop;
		private final int columns;

		private DependencyCard(Panel parent, DependencyState state) {
			super(parent);
			this.state = state;
			setWidth(parent.width);
			title = new TextField(this).setMaxWidth(width - 34).setText(state.dependency().getTitle());
			title.setPos(19, 4);
			headerHeight = Math.max(18, title.height + 8);
			taskTop = headerHeight + 5;
			columns = Math.max(1, (width - 12) / TASK_SPACING);
			int rows = (state.tasks().size() + columns - 1) / columns;
			setHeight(rows == 0 ? headerHeight : taskTop + rows * TASK_SPACING + 4);
		}

		@Override
		public void addWidgets() {
			add(title);
			for (int i = 0; i < state.tasks().size(); i++) {
				Task task = state.tasks().get(i);
				TaskButton taskButton = new TaskButton(this, task) {
					@Override
					public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
						Color4I.BLACK.withAlpha(isMouseOver() ? 110 : 55).draw(graphics, x, y, w, h);
					}

					@Override
					public boolean mousePressed(MouseButton button) {
						if (!isMouseOver()) {
							return false;
						}
						if (button.isLeft()) {
							questScreen.openAdditionalQuest(task);
						}
						return true;
					}
				};
				taskButton.setPosAndSize(6 + i % columns * TASK_SPACING, taskTop + i / columns * TASK_SPACING, 20, 20);
				add(taskButton);
			}
		}

		@Override
		public void alignWidgets() {
		}

		@Override
		public boolean mousePressed(MouseButton button) {
			if (super.mousePressed(button)) {
				return true;
			}
			if (isMouseOver() && button.isLeft() && state.navigable()) {
				questScreen.openAdditionalQuest(state.dependency());
			}
			return isMouseOver();
		}

		@Override
		public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
			Color4I.GRAY.withAlpha(isMouseOver() ? 42 : 22).draw(graphics, x, y, w, h);
			Color4I.BLACK.withAlpha(isMouseOver() ? 130 : 100).draw(graphics, x, y, w, headerHeight);
			state.dependency().getProgressColor(questScreen.file.selfTeamData).draw(graphics, x, y, 2, h);
			state.dependency().getIcon().draw(graphics, x + 5, y + 4, TITLE_ICON_SIZE, TITLE_ICON_SIZE);
			if (quest.isDependencySatisfied(questScreen.file.selfTeamData, state.dependency())) {
				ThemeProperties.CHECK_ICON.get(quest).draw(graphics, x + w - 10, y + 4, 8, 8);
			}
		}
	}
}
