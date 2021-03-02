package com.gamification.marketguards.service.gamelogic;

import com.gamification.marketguards.domain.mission.Mission;
import com.gamification.marketguards.domain.mission.MissionType;
import com.gamification.marketguards.domain.player.Player;
import com.gamification.marketguards.domain.quest.CompletingQuestRule;
import com.gamification.marketguards.domain.quest.Quest;
import com.gamification.marketguards.domain.quest.QuestType;
import com.gamification.marketguards.dto.gamelogic.CompletedMissionDto;
import com.gamification.marketguards.dto.gamelogic.MissionProgressionDto;
import com.gamification.marketguards.filter.quest.QuestFilter;
import com.gamification.marketguards.providers.notification.NotificationProvider;
import com.gamification.marketguards.repository.player.PlayerRepository;
import com.gamification.marketguards.repository.quest.QuestFilterPredicate;
import com.gamification.marketguards.repository.quest.QuestRepository;
import com.gamification.marketguards.service.GameStatusService;
import com.google.common.collect.Lists;
import com.querydsl.core.types.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mission progression logic. One of the core services that evaluates progression of the mission when player(user) finishes a task.
 * This is part of a monolithic application using object-relational mapping.
 *
 *  @author David Dvořák
 *  2020
 */
@Service
@Transactional
public class MissionProgressionService {

    @Autowired
    private GameStatusService gameStatusService;

    @Autowired
    private SkillProgressionService skillProgressionService;

    @Autowired
    private QuestRepository questRepository;

    @Autowired
    private NotificationProvider notificationProvider;

    @Autowired
    private PlayerRepository playerRepository;

    // The only public method in this class, being called after a quest is finished.
    // It updates user status and checks if progression is available.
    public void progressMission(Quest finishedQuest, MissionProgressionDto missionProgressionDto){

        skillProgressionService.updateSkillProgression(
                missionProgressionDto.getSkillProgressionDtoList(),
                finishedQuest.getId(),
                finishedQuest.getPlayer().getId());

        List<CompletingQuestRule> completingQuestRules = new ArrayList<>();
        finishedQuest.getQuestType().getParentQuestTypeRules().forEach(parentRule -> {
            completingQuestRules.addAll(parentRule.getChildQuestType().getChildQuestTypeRules());
        });

        List<Quest> playerQuests = getRelevantPlayerQuests(finishedQuest, completingQuestRules);
        List<Quest> newQuests = unlockNewQuests(finishedQuest, completingQuestRules, playerQuests);
        checkMissionCompletion(playerQuests, finishedQuest, missionProgressionDto);

        finishedQuest.getPlayer().setMissionSet(newQuests.stream().map(Quest::getMission).collect(Collectors.toSet()));
        playerRepository.save(finishedQuest.getPlayer());

        gameStatusService.updateGameStatusByValues(
                finishedQuest.getPlayer().getId(),
                finishedQuest.getQuestType().getCurrency(),
                missionProgressionDto.getCompletedQuest().getExperiences()
        );
        createNotifications(missionProgressionDto, finishedQuest.getPlayer().getId());
    }

    // Checks if mission is completed
    private void checkMissionCompletion(List<Quest> playerQuests, Quest finishedQuest, MissionProgressionDto missionProgressionDto){
        List<Quest> playerQuestsByMissionType = playerQuests.stream()
                .filter(playerQuest -> playerQuest.getQuestType().getMissionType().equals(finishedQuest.getQuestType().getMissionType()) && playerQuest.getFinished() == null)
                .collect(Collectors.toList());
        Long remainingObligatoryQuests = countRemainingQuestsByObligation(playerQuestsByMissionType, true);
        Long remainingVoluntaryQuests = countRemainingQuestsByObligation(playerQuestsByMissionType, false);
        if (remainingObligatoryQuests == 0 ) {
            if (finishedQuest.getMission().getFinished() == null) {
                finishedQuest.getMission().setFinished(new Date());
                missionProgressionDto.setCompletedMission(new CompletedMissionDto(
                        finishedQuest.getMission().getMissionType().getTitle(),
                        remainingVoluntaryQuests,
                        finishedQuest.getMission().getId()
                ));
            }
            if (remainingVoluntaryQuests == 0 ) {
                finishedQuest.getMission().setFinishedCompletely(new Date());
            }
        }
    }

    // Get list of quests to be evaluated or that connect to the current quest
    private List<Quest> getRelevantPlayerQuests(Quest finishedQuest, List<CompletingQuestRule> completingQuestRules){

        QuestFilter questFilter = new QuestFilter(
                completingQuestRules.stream()
                        .map(completingQuestRule -> completingQuestRule.getParentQuestType().getId())
                        .collect(Collectors.toList()),
                completingQuestRules.stream()
                        .map(completingQuestRule -> completingQuestRule.getChildQuestType().getMissionType().getId())
                        .collect(Collectors.toSet())
        );
        Predicate predicate = QuestFilterPredicate.by(questFilter, finishedQuest.getPlayer().getId());
        return Lists.newArrayList(questRepository.findAll(predicate));
    }

    // Unlock new quests according to the rules defined in database
    private List<Quest> unlockNewQuests(Quest finishedQuest, List<CompletingQuestRule> completingQuestRules, List<Quest> playerQuests){

        List<Quest> newQuests = new ArrayList<>();
        Map<QuestType, List<CompletingQuestRule>> completingQuestRulesByChild =
                completingQuestRules.stream()
                        .collect(Collectors.groupingBy(CompletingQuestRule::getChildQuestType));
        completingQuestRulesByChild.forEach((questType, completingRules) -> {
            if (questPreconditionsCompleted(completingRules, playerQuests) ) {
                Quest newQuest = new Quest(
                        preparePlayerMission(completingRules.get(0).getChildQuestType().getMissionType(), playerQuests, finishedQuest.getPlayer()),
                        completingRules.get(0).getChildQuestType(),
                        finishedQuest.getPlayer()
                );
                if (newQuest.getMission().getQuests() == null) {
                    newQuest.getMission().setQuests(new HashSet<>());
                }
                newQuest.getMission().getQuests().add(newQuest);
                newQuests.add(newQuest);
                playerQuests.add(newQuest);
            }
        });
        return newQuests;
    }

    // Prepares new unlocked mission
    private Mission preparePlayerMission(MissionType missionType, List<Quest> playerQuests, Player player){
        Optional<Mission> optionalMission = playerQuests.stream()
                .filter(o -> o.getQuestType().getMissionType().equals(missionType))
                .map(Quest::getMission)
                .findFirst();

        return optionalMission.orElseGet(() -> new Mission(missionType, player));
    }

    // Checks if new quest is unlockable
    private boolean questPreconditionsCompleted(List<CompletingQuestRule> completingQuestRules, List<Quest> playerQuests){

        List<QuestType> necessaryParentQuestTypes = completingQuestRules.stream()
                .map(CompletingQuestRule::getParentQuestType)
                .collect(Collectors.toList());

        return playerQuests.stream()
                .filter(quest -> quest.getFinished() != null)
                .map(Quest::getQuestType)
                .collect(Collectors.toList())
                .containsAll(necessaryParentQuestTypes);
    }

    private Long countRemainingQuestsByObligation(List<Quest> playerQuestsByMissionType, Boolean obligation){

        return playerQuestsByMissionType.stream()
                .filter(quest -> quest.getQuestType().getObligation().equals(obligation))
                .count();
    }

    // Creates notifications in case of progression
    private void createNotifications(MissionProgressionDto missionProgressionDto, Long idPlayer){

        notificationProvider.processFinishedQuestNotification(missionProgressionDto.getCompletedQuest(), idPlayer);
        if (missionProgressionDto.getCompletedMission() != null) {
            notificationProvider.processFinishedMissionNotification(missionProgressionDto.getCompletedMission(), idPlayer);
        }

    }

}
