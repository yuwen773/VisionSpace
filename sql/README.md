# sql/：MySQL 时代的归档件，不再被执行

业务库已迁移到 PostgreSQL 18（ADR 0004）。**这个目录里的东西都是历史记录，任何流程都不执行它们**：

| 文件 | 状态 |
|---|---|
| `create_vision_space.sql` | MySQL 8 的建库 dump，仅作迁移对照（基线 `src/main/resources/db/migration/V1__baseline.sql` 由它逐表换算而来）。 |
| `migrations/*.sql` | MySQL 时代的 42 份一次性迁移与回滚脚本，归档保留。 |
| `migrations/maintenance-window.manifest.json` | 维护窗口清单；`MaintenanceWindowManifestTest` 仍会读它校验人工批准点，但不回放 SQL。 |

- **建表只有一个入口**：Flyway。见 `docs/adr/0004-postgresql-schema-and-time-contract.md` §1。
- 入库、CI、验收、部署都不得 `psql -f` 这里的文件，也不得把它们挂进 PostgreSQL 容器的 init 目录。
- 少数测试（`MaintenanceWindowManifestTest`、`PictureFileMigrationTest`、`AgentAttachmentMigrationTest`）把这些文件当作文本读取，用来锁定历史决策；移动或删除文件会让它们失败——要动先改测试。
- 新增数据库变更一律写在 `src/main/resources/db/migration/V2__*.sql` 起，只增不改。
