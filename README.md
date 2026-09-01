# 伤害溯源 / Damage Stats

**当前版本 / Current version:** `0.1.1`  
**适用环境 / Target:** Minecraft `1.20.1` · Forge `47+`  
**许可证 / License:** MIT

伤害溯源会把一次攻击从面板属性、暴击与附魔，一路拆解到 Forge 事件、护甲、魔法减伤、吸收和最终实际扣血。它面向想弄清“这一刀为什么是这个数字”的玩家、整合包作者与模组开发者，并明确区分精确记录、合理推断和未知来源。

Damage Stats turns one hit into a readable damage pipeline—from attributes, cooldown, critical hits, and enchantments through Forge events, armor, magic reduction, absorption, and actual health loss. It is designed for players, modpack authors, and mod developers who want to know why a hit produced a particular number, while keeping exact data, inference, and unknown attribution clearly separated.

界面完整支持简体中文与英语，并提供日本语、俄语、繁體中文（台湾／香港）适配；Minecraft 1.20.1 的其他标准语言会使用英语回退。

The interface fully supports Simplified Chinese and English, with Japanese, Russian, and Traditional Chinese (Taiwan/Hong Kong) adaptations. Other standard Minecraft 1.20.1 locales use the English fallback.

## 命令 / Commands

| 命令 | 中文 | English |
| --- | --- | --- |
| `/damagestats on` | 开启自己的伤害记录。 | Enable damage tracing for yourself. |
| `/damagestats off` | 停止自己的伤害记录。 | Disable damage tracing for yourself. |
| `/damagestats last [编号]` | 打开最新记录；指定编号时打开该条记录。 | Open the latest trace, or the trace with the supplied ID. |
| `/damagestats history [1..100]` | 按记录顺序打开历史列表，可限制条数。 | Open trace history in record order, optionally limiting the count. |
| `/damagestats history time [1..100]` | 按时间查看历史记录。 | View history ordered by time. |
| `/damagestats history damage [1..100]` | 按最终伤害查看历史记录。 | View history ordered by final damage. |
| `/damagestats compare [编号A 编号B]` | 无编号时对比最近两次；指定编号时对比两条记录。 | Compare the two latest traces, or two traces selected by ID. |

## 模块 / Modules

| 模块 | 中文说明 | English description |
| --- | --- | --- |
| 伤害构成 / Damage Breakdown | 展示一次攻击的完整计算链路与每一步来源。 | Shows the complete calculation pipeline and the source of every step. |
| 真实顺序 / Actual Order | 按游戏实际发生顺序排列步骤。 | Lists stages in the order they occurred in game. |
| 即时增量 / Instant Delta | 显示每一步相对前一步造成的数值变化。 | Shows the immediate numerical change caused by each step. |
| 移除损失 / Removal Loss | 移除某一步并重放后续运算，估算它对最终伤害的真实贡献。 | Removes one step and replays later operations to estimate its final contribution. |
| 按 Mod / By Mod | 将来源按模组归组，便于定位伤害来自哪里。 | Groups sources by mod to make attribution easier. |
| 增加与减少 / Increases & Reductions | 将增伤、减伤和其他步骤分区显示。 | Separates increases, reductions, and other stages. |
| 来源详情 / Source Details | 展示来源类、运算、说明、UUID 与关联物品等证据。 | Shows evidence such as source class, operation, note, UUID, and related item. |
| 手动标注 / Manual Annotation | 为无法自动识别的细分来源指定物品图标；标注会按阶段、Mod、来源、运算与现场证据隔离。 | Assigns an item icon to a narrowly matched unknown source; annotations are isolated by stage, mod, source, operation, and observed evidence. |
| 历史 / History | 浏览当前服务器会话中的伤害记录。 | Browses damage traces from the current server session. |
| 对比 / Comparison | 对比两次攻击在各阶段的差异。 | Compares stage-by-stage differences between two attacks. |
| 精确 / Exact | 来自直接采样或确定计算。 | Obtained from direct sampling or deterministic calculation. |
| 推断 / Inferred | 由事件变化、运行时证据或已知公式推断。 | Inferred from event changes, runtime evidence, or known formulas. |
| 未知 / Unknown | 当前证据不足，不伪造归因。 | Attribution is intentionally withheld when evidence is insufficient. |

## 追踪内容 / What Is Traced

伤害溯源可记录攻击属性、攻击冷却、暴击、附魔、Forge 伤害事件、护甲、魔法减伤、吸收与实际生命损失；可将属性修饰符关联到装备，并在安装 Curios 时尝试关联饰品。对无法精确插桩的外部监听器会给出带置信度的候选，而不会把推测伪装成事实。

Damage Stats records attack attributes, cooldown, critical hits, enchantments, Forge damage events, armor, magic reduction, absorption, and actual health loss. Attribute modifiers can be associated with equipment and, when Curios is present, accessory slots. External listeners that cannot be sampled exactly are presented as confidence-labelled candidates instead of fabricated certainty.
