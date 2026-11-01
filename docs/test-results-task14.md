# Результаты тестов — задача #14

Дата прогона: 01.11.2026. Ветка `feature/connect-pets-tasks`.

## Как запускались

`./gradlew :app:testDebugUnitTest` в среде разработки не запустился — см. раздел «Сборка» в README. Поэтому весь не-Android код (модели, JSON, API, репозитории, логика экранов) и тесты собраны компилятором Kotlin 2.0.21 напрямую (`-Werror`, без предупреждений) и запущены JUnit-совместимым раннером. Compose-экраны, ViewModel, Activity и OkHttpTransport в этот прогон не входят — их нужно собрать в Android Studio.

## Итог

| Класс | Тестов | Из них с недели 1 |
|---|---|---|
| MapperTest | 17 | 11 (1 заменён по требованию #14) |
| TaskPresentationTest | 6 | 6 (без изменений) |
| FakeRepositoryTest | 15 | 8 (адаптированы) |
| HomeControllerTest | 16 | 7 (адаптированы) |
| ApiTest | 14 | — |
| EditControllerTest | 13 | — |
| **Всего** | **81 пройдено, 0 упало** | 32 |

## Полный вывод

```
== MapperTest
  OK   badTimezone_isReported_notReplacedWithUtc
  OK   brokenOccurrenceDate_doesNotCrash
  OK   completionFields_areMapped
  OK   disabledSchedule_isMapped
  OK   missingStatus_isUnknown_notComputedOnClient
  OK   onceTask_hasNoOccurrenceDate_dailyTaskHasIt
  OK   petBody_sendsSeparateFields_andNullForEmpty
  OK   petInBackendFormat_takesTypeFromDictionary
  OK   petTypeString_winsOverDictionary_andBlankFieldsBecomeNull
  OK   realServerPet_hasSeparateAllergiesAndIntolerances
  OK   realServerSpace_roleAndTimezone
  OK   realServerTaskItem_isParsed
  OK   serverStatus_isParsedCaseInsensitive
  OK   taskBody_timeIsInSpaceZone_notDeviceZone
  OK   taskInBackendFormat_mapsWithoutNewFields
  OK   taskItem_toDraft_usesSpaceZone
  OK   unknownPetType_givesNullTypeName
== TaskPresentationTest
  OK   dayBoundary_dependsOnSpaceZone
  OK   disabledDailySchedule_isFlagged
  OK   relativeDays
  OK   sorting_overdueFirst_thenUpcomingByTime_thenDoneNewestFirst
  OK   time_isShownInSpaceZone_notDeviceZone
  OK   ui_fallbacks_and_completedText
== FakeRepositoryTest
  OK   createAndUpdatePet_areVisibleAfterReload
  OK   createTask_thenUpdate_inSpaceZone
  OK   dates_areShiftedToToday
  OK   emptyScenario_returnsNoTasks
  OK   errorScenario_throwsIOException
  OK   missingResponsibleName_staysNull
  OK   noResponse_savesButThrows_soResultIsUnconfirmed
  OK   petWithoutName_isRejected422
  OK   pets_ofSpace_getTypeFromDictionary
  OK   taskWithAssigneeFromOtherSpace_isRejected
  OK   tasks_areFilteredByPet
  OK   testDataFile_parsesInServerFormat
  OK   testData_coversAllStatuses_andBothRepeatTypes
  OK   twoSpaces_doNotMix
  OK   unknownSpace_isForbidden
== HomeControllerTest
  OK   accessLost_removesThatSpaceOnly
  OK   error_thenRetry_showsContent
  OK   invalidTimezone_showsError_notUtcTimes
  OK   lateResponseForPreviousPet_isDiscarded
  OK   noPets_isNoPetsState
  OK   noSpaces_andSpacesError
  OK   noTasks_isEmptyState
  OK   rapidSwitching_backAndForth_showsOnlyLastSelection
  OK   refreshFailure_keepsOldData_andMarksStale
  OK   refresh_keepsOldDataUntilNewArrives
  OK   refresh_loadsBothProfileAndTasks
  OK   slowResponse_showsLoading_andAllowsSwitchingMeanwhile
  OK   start_selectsFirstPet_andShowsItsTasks
  OK   switchSpace_clearsOldDataImmediately
  OK   tasksOfAnotherPet_inResponse_areNotShown
  OK   twoSpaces_twoPets_lateResponsesOfOldSpaceAreDiscarded
== ApiTest
  OK   brokenJson_isFormatError_notCrash
  OK   createPet_postsToSpace_withSeparateAllergyFields
  OK   createTask_postsToSpace_withTimeInSpaceZone
  OK   createTask_withInvalidTimezone_isNotSent
  OK   devLogin_addsTokenAndReloginsOn401
  OK   forbidden_and_nonJsonError_areReadable
  OK   networkFailure_isIOException
  OK   petOfAnotherSpace_isRejected
  OK   pets_requestContainsSelectedSpace
  OK   tasksOfOtherPetOrSpace_areDropped
  OK   tasks_requestContainsSpaceAndPet
  OK   updatePet_checksSpaceFirst_thenPatches
  OK   updatePet_ofAnotherSpace_isNotSent
  OK   validationError_hasFieldMessages
== EditControllerTest
  OK   pet_doubleTap_sendsOneRequest
  OK   pet_noResponse_isUnconfirmed_andTriggersReload
  OK   pet_notSavedUntilServerAnswers
  OK   pet_server422_showsFieldError
  OK   pet_serverError_isFailed_notSaved
  OK   pet_withoutNameOrType_isNotSent
  OK   task_assigneeFromOtherSpace_isRejectedOnClient
  OK   task_badDateTime_andMissingFields_areNotSent
  OK   task_dateAndTime_areInSpaceZone_andSavedAfterAnswer
  OK   task_editAfterSaved_resetsSavedLabel
  OK   task_invalidTimezone_blocksSaving
  OK   task_noResponse_isUnconfirmed
  OK   task_server422_mapsServerFieldNames

Итого: 81 passed, 0 failed
```
