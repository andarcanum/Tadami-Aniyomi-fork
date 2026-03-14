# LnReader Aurora Design Migration

## What This Is

LnReader (fork of Aniyomi) — Android-приложение для чтения японских ранобэ (light novels). Приложение с открытым исходным кодом, поддерживающее несколько языков и источников контента.

## Core Value

Обеспечить консистентный пользовательский интерфейс через миграцию устаревших настроек на общий дизайн Aurora.

## Requirements

### Validated

- ✓ Существующая функциональность чтения Ranobe — существующая
- ✓ Система настроек с preferences — существующая
- ✓ Aurora UI компоненты — существующая

### Active

- [ ] Конвертировать настройку "Home Header Layout" в Aurora design

### Out of Scope

- Добавление новой функциональности — только миграция существующей
- Рефакторинг всей системы настроек — только целевая настройка

## Context

Проект представляет собой Android-приложение на Kotlin с Clean Architecture:
- Domain layer: бизнес-логика и модели
- Data layer: репозитории и хранилище
- Presentation layer: Jetpack Compose UI

Задача: одна настройка "Расположение шапки Home" не использует общий дизайн Aurora, в отличие от других настроек.

## Constraints

- **Tech Stack**: Kotlin, Jetpack Compose, Clean Architecture
- **Совместимость**: Должно работать на Android 8.0+
- **Дизайн**: Aurora UI components — общие компоненты UI

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Миграция только одной настройки | Минимальный риск, быстрая итерация | — Pending |
| Использование Aurora компонентов | Консистентность с остальными настройками | — Pending |

---
*Last updated: 2026-03-14 after project initialization*
