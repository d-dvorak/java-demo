package com.gamification.marketguards.providers.notification;

import com.gamification.marketguards.domain.notification.NotificationValue;
import com.gamification.marketguards.dto.gamelogic.CompletedMissionDto;
import com.gamification.marketguards.dto.gamelogic.CompletedQuestDto;
import com.gamification.marketguards.dto.gamelogic.NewSkillLevelDto;
import com.gamification.marketguards.dto.gamelogic.NewSubSkillLevelDto;
import com.gamification.marketguards.enums.*;
import com.gamification.marketguards.service.NotificationService;
import com.gamification.marketguards.websocket.SseNotificationDto;
import com.gamification.marketguards.websocket.SseParamDto;
import com.gamification.marketguards.websocket.SsePushNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This example contains factory for building new notifications.
 * Unlike other examples, this class doesn't contain as much logic as value transformation and assignment.
 * All the public methods in this file set up different type of notification.
 *
 * @author David Dvořák
 * 2020
 */
@Service
public class NotificationProvider {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SsePushNotificationService ssePushNotificationService;

    // Max text length for truncation. UI doesn't need longer text for notification preview.
    private static final int MAX_VALUE_LENGTH = 50;

    public void processNewLevelNotification(Long idPlayer, int level, int experiences, int nextLevelExperiences) {
        ArrayList<SseActionEnum> sseActions = new ArrayList<>();
        sseActions.add(SseActionEnum.RELOAD_GAME_STATUS);
        sseActions.add(SseActionEnum.RELOAD_NOTIFICATIONS);

        Set<NotificationValue> notificationValues = new ArrayList<NotificationValue>().stream().collect(Collectors.toSet());
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.level, Integer.toString(level))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.experiences, Integer.toString(experiences))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.nextLevelExperiences, Integer.toString(nextLevelExperiences))
        );

        notificationService.createNotification(idPlayer, NotificationTypeEnum.NEW_LEVEL, notificationValues);
        ssePushNotificationService.doNotify(idPlayer, new SseNotificationDto(
                SseCodeEnum.NEW_LEVEL, SseTypeEnum.MODAL, new Date(), sseActions, new ArrayList<>())
        );
    }

    public void processFinishedQuestNotification(CompletedQuestDto completedQuestDto, Long idPLayer) {
        ArrayList<SseActionEnum> sseActions = new ArrayList<>();
        sseActions.add(SseActionEnum.RELOAD_GAME_STATUS);
        sseActions.add(SseActionEnum.RELOAD_NOTIFICATIONS);
        sseActions.add(SseActionEnum.RELOAD_QUEST);
        sseActions.add(SseActionEnum.RELOAD_QUESTS);

        Set<NotificationValue> notificationValues = new ArrayList<NotificationValue>().stream().collect(Collectors.toSet());
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.questId, Long.toString(completedQuestDto.getQuestId()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.missionId, Long.toString(completedQuestDto.getMissionId()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.questTitle, truncateString(completedQuestDto.getTitle()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.questObligation, Boolean.toString(completedQuestDto.getObligation()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.questXP, Integer.toString(completedQuestDto.getExperiences()))
        );

        notificationService.createNotification(idPLayer, NotificationTypeEnum.QUEST_COMPLETED, notificationValues);
        ssePushNotificationService.doNotify(idPLayer, new SseNotificationDto(
                SseCodeEnum.QUEST_COMPLETE, SseTypeEnum.MODAL, new Date(), sseActions, new ArrayList<>())
        );
    }

    public void processFinishedMissionNotification(CompletedMissionDto completedMissionDto, Long idPLayer) {
        ArrayList<SseActionEnum> sseActions = new ArrayList<>();
        sseActions.add(SseActionEnum.RELOAD_GAME_STATUS);
        sseActions.add(SseActionEnum.RELOAD_NOTIFICATIONS);
        sseActions.add(SseActionEnum.RELOAD_MISSIONS);

        Set<NotificationValue> notificationValues = new ArrayList<NotificationValue>().stream().collect(Collectors.toSet());
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.missionId, Long.toString(completedMissionDto.getMissionId()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.missionTitle, truncateString(completedMissionDto.getMissionTitle()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.actualActiveQuestCount, Long.toString(completedMissionDto.getRemainingQuestCount()))
        );

        ArrayList<SseParamDto> sseParamsDto = new ArrayList<SseParamDto>();
        sseParamsDto.add(new SseParamDto(SseParamEnum.MISSION_ID, Long.toString(completedMissionDto.getMissionId())));

        notificationService.createNotification(idPLayer, NotificationTypeEnum.MISSION_COMPLETED, notificationValues);
        ssePushNotificationService.doNotify(idPLayer, new SseNotificationDto(
                SseCodeEnum.MISSION_COMPLETE, SseTypeEnum.MODAL, new Date(), sseActions, sseParamsDto)
        );
    }

    private String truncateString(String value){
        if (value.length() > MAX_VALUE_LENGTH) {
            return value.substring(0, MAX_VALUE_LENGTH- 3) + "...";
        } else {
            return value;
        }
    }

    public void processNewSkillLevelNotification(Long idPlayer, NewSkillLevelDto newSkillLevelDto) {
        ArrayList<SseActionEnum> sseActions = new ArrayList<>();
        sseActions.add(SseActionEnum.RELOAD_GAME_STATUS);
        sseActions.add(SseActionEnum.RELOAD_NOTIFICATIONS);

        Set<NotificationValue> notificationValues = new HashSet<>();
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.skillId, Long.toString(newSkillLevelDto.getSkillId()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.skillLevel, Integer.toString(newSkillLevelDto.getSkillLevel()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.skillTitle, newSkillLevelDto.getSkillTitle())
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.skillPoints, Integer.toString(newSkillLevelDto.getSkillPoints()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.nextLevelSkillPoints, Integer.toString(newSkillLevelDto.getNextLevelSkillPoints()))
        );

        notificationService.createNotification(idPlayer, NotificationTypeEnum.NEW_SKILL_LEVEL, notificationValues);

        ssePushNotificationService.doNotify(idPlayer, new SseNotificationDto(
                SseCodeEnum.NEW_SKILL, SseTypeEnum.VOID, new Date(), sseActions, new ArrayList<>())
        );
    }

    public void processNewSubSkillLevelNotification(Long idPlayer, NewSubSkillLevelDto newSubSkillLevelDto) {
        ArrayList<SseActionEnum> sseActions = new ArrayList<>();
        sseActions.add(SseActionEnum.RELOAD_GAME_STATUS);
        sseActions.add(SseActionEnum.RELOAD_NOTIFICATIONS);

        Set<NotificationValue> notificationValues = new HashSet<>();
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.skillId, Long.toString(newSubSkillLevelDto.getSkillId()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.subSkillId, Long.toString(newSubSkillLevelDto.getSubSkillId()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.subSkillLevel, Integer.toString(newSubSkillLevelDto.getSubSkillLevel()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.subSkillTitle, newSubSkillLevelDto.getSubSkillTitle())
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.subSkillPoints, Integer.toString(newSubSkillLevelDto.getSubSkillPoints()))
        );
        notificationValues.add(
                new NotificationValue(NotificationValueTypeEnum.nextLevelSubSkillPoints, Integer.toString(newSubSkillLevelDto.getNextLevelSubSkillPoints()))
        );

        notificationService.createNotification(idPlayer, NotificationTypeEnum.NEW_SUB_SKILL_LEVEL, notificationValues);
        ssePushNotificationService.doNotify(idPlayer, new SseNotificationDto(
                SseCodeEnum.NEW_SUB_SKILL, SseTypeEnum.VOID, new Date(), sseActions, new ArrayList<>())
        );
    }

}
