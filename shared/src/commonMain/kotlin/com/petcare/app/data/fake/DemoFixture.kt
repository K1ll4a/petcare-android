package com.petcare.app.data.fake

/** One JSON fixture used by Android, iOS and tests. */
object DemoFixture {
    val json: String = """
{
  "base_date": "2026-10-22",
  "pet_types": [
    { "id": 1, "name": "кот" },
    { "id": 2, "name": "собака" },
    { "id": 3, "name": "попугай" }
  ],
  "spaces": [
    { "space_id": 1, "space_name": "Семья", "space_timezone": "Europe/Moscow", "role": "owner" },
    { "space_id": 2, "space_name": "Дача", "space_timezone": "Asia/Yekaterinburg", "role": "member" },
    { "space_id": 3, "space_name": "Сломанный пояс (демо ошибки)", "space_timezone": "Mars/Olympus", "role": "owner" }
  ],
  "members": {
    "1": [
      { "user_id": 3, "user_name": "Гриша", "role": "owner" },
      { "user_id": 4, "user_name": "Аня", "role": "member" }
    ],
    "2": [
      { "user_id": 5, "user_name": "Олег", "role": "owner" },
      { "user_id": 3, "user_name": "Гриша", "role": "member" }
    ],
    "3": [
      { "user_id": 3, "user_name": "Гриша", "role": "owner" }
    ]
  },
  "pets": [
    {
      "pet_id": 10, "space_id": 1, "pet_name": "Tom", "pet_type_id": 1,
      "pet_features": "боится пылесоса", "pet_allergies": "курица",
      "pet_intolerances": "молоко", "pet_care_notes": "корм только сухой, 2 раза в день"
    },
    { "pet_id": 11, "space_id": 1, "pet_name": "Рекс", "pet_type_id": 2,
      "pet_features": null, "pet_allergies": null, "pet_intolerances": null, "pet_care_notes": null },
    { "pet_id": 12, "space_id": 1, "pet_name": "Кеша", "pet_type_id": 3 },
    { "pet_id": 20, "space_id": 2, "pet_name": "Мурка", "pet_type_id": 1, "pet_allergies": "рыба" },
    { "pet_id": 21, "space_id": 2, "pet_name": "Шарик", "pet_type_id": 2, "pet_intolerances": "лактоза" },
    { "pet_id": 30, "space_id": 3, "pet_name": "Пушок", "pet_type_id": 1 }
  ],
  "tasks": [
    {
      "item_id": "1:2026-10-22", "id": 1, "occurrence_id": 101, "occurrence_date": "2026-10-22",
      "space_id": 1, "pet_id": 10, "task_name": "покормить", "date_time": 1792645200,
      "responsible_id": 4, "responsible_name": "Аня", "is_daily": true, "is_active": true, "status": "done",
      "completed_by_id": 3, "completed_by_name": "Гриша", "completed_at": 1792645800
    },
    {
      "item_id": "1:2026-10-21", "id": 1, "occurrence_id": 100, "occurrence_date": "2026-10-21",
      "space_id": 1, "pet_id": 10, "task_name": "покормить", "date_time": 1792558800,
      "responsible_id": 4, "responsible_name": "Аня", "is_daily": true, "is_active": true, "status": "missed",
      "completed_by_id": null, "completed_by_name": null, "completed_at": null
    },
    {
      "item_id": "2:2026-10-22", "id": 2, "occurrence_id": 102, "occurrence_date": "2026-10-22",
      "space_id": 1, "pet_id": 10, "task_name": "расчесать", "date_time": 1792692000,
      "responsible_id": 3, "responsible_name": "Гриша", "is_daily": true, "is_active": true, "status": "upcoming"
    },
    {
      "item_id": "3:once", "id": 3, "occurrence_id": null, "occurrence_date": null,
      "space_id": 1, "pet_id": 10, "task_name": "дать таблетку от глистов", "date_time": 1792659600,
      "responsible_id": 4, "responsible_name": "Аня", "is_daily": false, "is_active": true, "status": "overdue"
    },
    {
      "item_id": "4:once", "id": 4, "space_id": 1, "pet_id": 10, "task_name": "сводить к ветеринару",
      "date_time": 1792843200, "responsible_id": 3, "responsible_name": "Гриша",
      "is_daily": false, "is_active": true, "status": "upcoming"
    },
    {
      "item_id": "5:2026-10-22", "id": 5, "occurrence_date": "2026-10-22", "space_id": 1, "pet_id": 10,
      "task_name": "дать витамины", "date_time": 1792652400, "responsible_id": 4, "responsible_name": "Аня",
      "is_daily": true, "is_active": false, "status": "cancelled"
    },
    {
      "item_id": "6:2026-10-22", "id": 6, "occurrence_date": "2026-10-22", "space_id": 1, "pet_id": 11,
      "task_name": "утренняя прогулка", "date_time": 1792643400, "responsible_id": 3, "responsible_name": "Гриша",
      "is_daily": true, "is_active": true, "status": "done",
      "completed_by_id": 4, "completed_by_name": "Аня", "completed_at": 1792644720
    },
    {
      "item_id": "7:2026-10-22", "id": 7, "occurrence_date": "2026-10-22", "space_id": 1, "pet_id": 11,
      "task_name": "вечерняя прогулка", "date_time": 1792688400, "responsible_id": 3, "responsible_name": "Гриша",
      "is_daily": true, "is_active": true, "status": "upcoming"
    },
    {
      "item_id": "8:once", "id": 8, "space_id": 1, "pet_id": 11, "task_name": "помыть лапы",
      "date_time": 1792684800, "responsible_id": 4, "is_daily": false, "is_active": true, "status": "upcoming"
    },
    {
      "item_id": "20:2026-10-22", "id": 20, "occurrence_date": "2026-10-22", "space_id": 2, "pet_id": 20,
      "task_name": "покормить", "date_time": 1792641600, "responsible_id": 5, "responsible_name": "Олег",
      "is_daily": true, "is_active": true, "status": "done",
      "completed_by_id": 3, "completed_by_name": "Гриша", "completed_at": 1792642800
    },
    {
      "item_id": "20:2026-10-21", "id": 20, "occurrence_date": "2026-10-21", "space_id": 2, "pet_id": 20,
      "task_name": "покормить", "date_time": 1792555200, "responsible_id": 5, "responsible_name": "Олег",
      "is_daily": true, "is_active": true, "status": "missed"
    },
    {
      "item_id": "21:once", "id": 21, "space_id": 2, "pet_id": 21, "task_name": "привить от бешенства",
      "date_time": 1792731600, "responsible_id": 3, "responsible_name": "Гриша",
      "is_daily": false, "is_active": true, "status": "upcoming"
    },
    {
      "item_id": "30:once", "id": 30, "space_id": 3, "pet_id": 30, "task_name": "подстричь когти",
      "date_time": 1792670400, "responsible_id": 3, "responsible_name": "Гриша",
      "is_daily": false, "is_active": true, "status": "upcoming"
    }
  ]
}

""".trimIndent()
}
