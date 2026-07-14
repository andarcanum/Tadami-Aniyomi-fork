package eu.kanade.domain.easteregg.aurora

// СГЕНЕРИРОВАНО tools/aurora_forge.mjs — не редактировать вручную.
// В этом файле НЕТ секрета: только соли, контрольные хеши ключей (PBKDF2,
// 120000 итераций) и AES-256-GCM-шифртекст. Ответы и награда в кодовой
// базе не существуют — их невозможно извлечь анализом кода.
@Suppress("ktlint:standard:max-line-length")
object AuroraVaultData {

    // Версия ваулта для мягкой миграции прогресса (AuroraHeartManager).
    const val VERSION = 192831836

    const val FIRST_RIDDLE = "Аврора шепчет тем, кто не спит: назови время, когда граница между небом и сном тоньше всего. Ответ отдай поиску."

    val STAGES = listOf(
        AuroraStage(
            salt = "drwDHJFKAthQzbq/kzFLVg==",
            iv = "J/D0vdPxvNGtyvPN",
            check = "hNVGExa5PjStWL950XwgGbh9vb1tJpzFWU7gSEsDhmI=",
            data = "Y8Ooxi7NLFlUWWrEkHzpEUueImpGAtRkkOBTHa9wlNJvbdQF/9HWFogOYUUuN47y5AsDt5LvbhvaQkLFe7XMz5RHt6AGicOW5QU80EQ4aCttHitDU83W39GbRb9MgMnAo6JJjQkt11hBUxTZrmZBKW8bMz03VXbRcNfb/UcFKgGVD7PuOjZy50I4o5hZzff1hy8Of7BfAkJvdemPJE164fbC9htnzaycYQ9mMJmlVGTiJbGZAPgXrEjRfBhftThrQCKHDO3A+YyuHwdsNxqdWAt7Cd/BYU7IRZ5mW82aTWo+etx6T5XrtDtVouklqiYilYV+AOlKbL9rMetOHlfgOyZtabSdVotZWISFD04ScjT2fhb7q94HbZ6rDDZHCr1WbPvSTvDWVKhCvyFMR4nnWCG+dsGJ2Tvnud7YZPAcAXfBOq14c5RrEU/OjwH7ex9W5ipvhU+mdDCh3aH67/HtBKHse2juWdaC1XtugwdF/xhzpttDibCqjmSvTCfLJw68NV8S1l1lyA9GSZ6UBs3YFTZQDHuoA++yfsCjTcy91yblBedxL/P6uvN3y8AOwm+E5vDvu/L0AWR8lHlG",
        ),
        AuroraStage(
            salt = "7gY+G/NobU0jPz8qJXgpcQ==",
            iv = "PQrLSjAwp8XLxwiz",
            check = "iY0zI1wzNcKxGTfWAYhvIMb9Uto9N6aP+bX71u+za0k=",
            data = "uCBwSNNAbvQXlYaNrwpCij7GDqURHP+8/K7gghwJ0Wms/1n0Pi2fU+rslHbcFwYk5OuZ1PPgY6E8FtG3EUWikOVNrDi0PiNBLN5pykICCSVAwgC2wAOwm9I6czahmV/Qo9zpLMQnsMTpcEE4Ndr/ytVh/QS+iGWXARMCRIRo2EQlJXyujwztPAwaYO5GKoZmuRYzizAGMVneuxoO5LjeCkhQvUhyg2HWy70Otahe7ELpxfsoHb6ddNss2pX0gRsr9PnpAtEukRquHwdcKTmiLTxgRlm9y8+4H/YaOD10LLDHUr4DMXxQsmuPykBc4iWyIm7AdF6tKZb4UJjljf2ymXqvKOA7fmbwpYtAhgFA4O7RHtolvTCTznUrgQDoyZX/Y0jDeiWc4leY5Swik96K31DFUcVFJmYhUIUSc+Sk3/k4ulMLkaEowW0d7YsDleOV/DegDNFz4lrth/ZC9S/XcHA+uY3ebiQCocibRt78GblXHhTLaC+jqBjmadPCWY5AHphEdHL1poymCj6ITwT1JV62YFFm+dvonNWY0Nli1S5iasVSJAxAEuxNBlAjIKo12n6ThunRVT9INMqF5c7/b7h1qg==",
        ),
        AuroraStage(
            salt = "q6fzenClFyR3GkMw0l+SSA==",
            iv = "GebBPLRbbEFdKdpi",
            check = "/xqMMObNQYY5sMLQTjrcTAuagffOLrh/Mw/U8p0COkg=",
            data = "Pbse4RYrV/QkzjQ6EDHD51D8waA85unbg5fl+IPDh38qDXDbYlb1FjNS0iUCj5ypTQhM5xg1hBDryigYfr8rEgB9A520fNg73vxEqAaaKM3T8gL+4eLBzNn1F2X8jUl9ilVb1BUxwJ8pHxOIP+VtY7R1ERvJt8buDViG9nqLVddkX+PL3325rT6A59mkDfo6R4u/kWKmtFPtVla3BXIjfEtt+rP6kqR22VNyW89f+08qqB3fukhRwm2iwsj42udu48Jgq21D514Rvoqe46Cu9sypjtbW/2NuS0CBrlK7R9fInxrjx5WegO8F9zPmk7Kt9Agku/VTAYEEmHEF/bqekADelor7n4tPWaSkb1KNBH7hSx8TQ3lwKvM+6mCxq0NsRH4t4J/uwVWa1po/7dr7XsXVH1e6P8qvO3wjFW5Bugv1LzhiNgN242zJL5y1cwhelNlCUnHYl0cUoH5sJ3Rg07gQl8WL2rYn4ooWJDjGmnWqlALT25zYgYHD5xMZYe4+kdH21/4SAIF9LHzK3f7ic8B0rgXtfl9ZiAiz1MK213k3JTF39rH20C5gZsDGqH1zFH4htASTk2LrAFDhMQ6+jXDRiZ2eThgQ8AknYHH4wmUSIGAM/iMSvRTTroJ2ELPLpQkze23MZk63ChYbQwWVt4cYeuHPjbclNV2WhI8bsAOiIejsM9A7ucLH99EDHPG6ZCHfVenEzYwh8nVfE2S/XzFLuu3sc7Po0K+YJHyYCtjMEeKLogseZW4eRLUzOEtmvOPJtQ9RVfJnng/HMj3AGG0MpgePHitp+00PY2WU5wHwao22w7FeprPZIadK6pzDDg5ybw4vCpcEiajw5bwAE8+spcrya5RSTv3KYPbWgyvBGMWF1ZS/37XGmBD5rsNQw8ViLEgtB9WKHu97Zun+wtKMDAD/82STK2yLOwVQ6e77/6gvSYMJBH+xpdLcAgjsjZomA42yvrvoFz7D/FqU0jfjto/3nqLMkV4lmiFzfvFWJZTnSrC/N+m74SZ/JFGTkikDKDztlPiB/2Ty7RVFNcJ3PGVgqRijbH+g7QLgrQAxTj7RqFM5LqRsu1IUbkST0rdln08ZT0szPOo7lHHuEiZzFRGBUb6OJqWC0We84Ypn2vuM0H7QFj+P3GqCnmJV+9r4Gm0h//q55gZ9fKYBe18IirgsAqOcRMGivGNwx3avQrG7tPW8GeTjl9YTN/U2U8iLHk6zPP98XkCUBE0XfwbrV324/2qMpLviV+5b9xLs9QaVpLhx7kioztSxzA/F8yEjOPXpytU841ks37w9M60zBPUrWGL9BuOLrgYdJu6HjPMB3EUrR196/MqFkxDlBN4wCcx+P2QLOavXW1C1qUZCCy5LSHR4L7qnKy6SPjQQ05rK6jDS4RL/zefvE1fHqIvRWf4Roen9N/hkKw9G4jKFc1P3eav2VN0i5bMnzwz+2bLxxd37LajJFVJcNtZ4GBGkrVWM/AW5qL7DdjbaY+3DIDWhxkAiGnYHoTJI2/7MZRDiX40k",
        ),
    )
}
