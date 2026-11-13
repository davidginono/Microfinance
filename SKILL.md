---
name: microfinance-ui-guidance
description: Route Microfinance backend work to the project-local Spring Boot skill and UI work to the project-local UI governance, style and table skills.
---

# Microfinance Backend and UI Guidance

Read and follow the required project-local skills before starting the affected work:

- For every backend task, read and follow [.agents/skills/springboot-1k-rps-optimization/SKILL.md](.agents/skills/springboot-1k-rps-optimization/SKILL.md), including implementation, fixes, refactoring and review of controllers, services, DTOs, repositories, models, database queries/migrations, transactions, authorization, concurrency and performance.
- For every UI task, read and follow both [.agents/skills/microfinance-ui-governance/SKILL.md](.agents/skills/microfinance-ui-governance/SKILL.md) for page flows, resource-pattern decisions, domain language and financial-action states, and [.agents/skills/microfinance-ui-style/SKILL.md](.agents/skills/microfinance-ui-style/SKILL.md) for layouts, dashboards, forms, validation, shared controls, modals and responsive presentation. This includes UI implementation, fixes, restyling and review.
- Also read and follow [.agents/skills/microfinance-table-standard/SKILL.md](.agents/skills/microfinance-table-standard/SKILL.md) whenever the task affects tables, registries, queues, reports or pagination.

Tasks that affect both backend and UI must follow both sets of requirements. Use these project-local skills rather than global skills with the same names or old SACCO-specific skills.

Apply the repository's [AGENTS.md](AGENTS.md) alongside these skills. This entry point preserves the existing root guidance location and skill identifier without duplicating the maintained rules.
