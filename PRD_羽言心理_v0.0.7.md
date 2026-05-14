# 羽言心理 App PRD 产品需求文档

## 文档信息
| 项目 | 内容 |
|------|------|
| 产品名称 | 羽言心理（PsyMap） |
| 当前版本 | v0.2.0 |
| 平台 | Android（Kotlin + Jetpack Compose） |
| 目标用户 | 北师大MAP咨询方向考研备考学生 |
| 产品定位 | AI智能考研备考工具 |
| Slogan | 新知 · 成长 · 快乐 |

---

## 一、用户体系

### 1.1 登录方式
- **微信登录**：调用微信SDK，AppID: wx842e5368d23fb03c，获取用户头像、昵称
- **手动昵称**：微信未安装时支持手动输入昵称登录
- **权限管理**：所有微信登录用户自动获得管理员权限

### 1.2 用户角色
| 角色 | 权限 |
|------|------|
| 管理员 | 新建/删除题库、上传/编辑/删除题目、设定分数、制定学习计划、分享题库 |
| 普通用户 | 学习、复习、查找题目（当前版本所有用户均为管理员） |

---

## 二、首页功能

### 2.1 搜索栏
- 支持搜索题目、题库、关键词
- 搜索结果可点击查看详情和编辑
- 点击"我的题库"可刷新回原始状态

### 2.2 考研倒计时卡片
- 显示距离2026年12月19日考研天数
- 显示连续打卡天数、累计打卡天数
- 橙色渐变背景，MIUI风格

### 2.3 快捷功能入口（8个）
| 第一行 | 第二行 |
|--------|--------|
| 学习计划 | 我的题库 |
| 打卡日历 | 制作音频 |
| 复习错题 | 磨耳朵 |
| 收藏题目 | 更多知识 |

### 2.4 我的题库列表
- 显示所有题库卡片（emoji + 名称 + 题数 + 可用题型）
- 长按可删除题库（管理员）
- 支持创建题库（题型多选：单选题/案例分析题/长难句/简答题/论述题/综合写作题）
- 支持文件导入（PDF/Word/Excel/图片/文本）

---

## 三、题库管理（题库Tab）

### 3.1 左侧分类栏
- "全部"显示所有题库
- 动态显示每个题库名称（实时更新）
- 底部标签筛选：常考/多背

### 3.2 右侧题目列表
- 按题型筛选（全部题型/单选题/案例分析题/简答题/论述题/综合写作题/单词短语/长难句/作文）
- 显示题数、错题数、可用题型
- 点击进入题库详情

### 3.3 题库详情
- 题目列表，支持点击查看/编辑
- 管理员长按进入多选模式，批量删除
- 支持修改题库名称
- 支持添加题目

---

## 四、学习功能（学习Tab）

### 4.1 题库学习
- 顺序学习 / 乱序学习
- 副标题显示"共 xx 题，今日已学习 xx 题"（去重计数，重复学习同一题只算一次）
- 进度条显示当日正确率（今日答对题数/今日学习题数，取最新一次结果）
- 选择题：选项卡片式展示，提交后显示正确/错误高亮
- 多选题：支持选择多个选项，答案支持"ABD"/"A,B,D"等格式
- 主观题：文本输入答案，AI评分

### 4.2 AI评分机制
- **本地有答案**：直接对比评分，按踩分点给分
- **本地无答案**：AI先生成标准答案保存到本地，再用该答案评分
- 评分结果显示分数 + 详细反馈
- 分数≥60分判定为正确

### 4.3 错题本
- 自动记录错题
- 答对后自动移出错题本
- 再次答错重新加入
- 点击可编辑题目详情

### 4.4 收藏本
- 手动收藏/取消收藏
- 点击可编辑题目详情

### 4.5 学习会话
- 全屏Dialog展示，支持手势返回
- 上一题/下一题按钮固定底部
- 切题时自动重置AI评分状态
- 题目/答案/选项支持Markdown格式渲染
- 答案为空时显示"AI正在生成参考答案..."

---

## 五、题目管理

### 5.1 题目属性
| 字段 | 说明 |
|------|------|
| 题目内容 | 支持Markdown格式 |
| 答案 | 支持Markdown格式，为空时AI自动生成 |
| 选项 | 选择题专用，每行一个选项 |
| 题目类型 | 单选/多选/案例分析/简答/论述/综合写作/单词短语/长难句/作文 |
| 标签 | 常考🔥 / 多背📖（可编辑） |
| 收藏/错题 | 通过操作结果自动更新 |
| 章节 | 可选 |

### 5.2 题目导入
- **拍照导入**：DeepSeek-OCR识别 + Qwen2.5-72B结构化
- **文件导入**：支持PDF/Word/Excel/图片/文本
- **手动添加**：管理员手动输入
- 导入时可选择题库、题型、标签
- 选择题自动识别选项格式
- 答案为空时AI自动异步生成

### 5.3 题目编辑
- 全屏Dialog展示
- 可修改题目类型（切换为选择题时自动显示选项输入框）
- 可修改常考/多背标签
- 保存时同步更新选项数据

---

## 六、学习计划

### 6.1 每日学习计划
- MIUI风格全屏页面
- 顶部橙色渐变卡片显示今日总进度
- 各题库进度条 + 完成状态
- 编辑每日目标题数
- 保存后自动创建日历提醒（每天16:00，重复90天）

### 6.2 AI制定计划
- 全屏Dialog，用户描述学习需求
- AI生成格式化学习计划
- 预览确认后应用到APP
- 修改计划不影响历史打卡记录（每日快照机制）

### 6.3 打卡日历
- 全屏Dialog，支持月份切换
- 绿色=已完成，橙色=部分完成，灰色=未打卡
- 月度统计
- 打卡规则：所有题库完成当日目标才算打卡

---

## 七、更多知识

### 7.1 心理学知识（Tab页）
- 基于许燕《人格心理学》大纲，6个学派章节
- **用户输入模式**：输入题目 → AI润色并生成答案
- **学派选择模式**：下拉选择学派 → AI生成3道论述题
- 输入框有内容时学派选择自动隐藏
- 生成的题目支持：Pin持久化、展开/收起答案、加入题库（弹窗选择题库+题型）
- 答案Markdown格式渲染
- 数据持久化存储，支持备份恢复

### 7.2 英文泛读（Tab页）
- AI推荐考研英语阅读文章（5篇/次）
- 来源：经济学人/卫报/纽约时报/科学美国人等
- 题材分布：心理学/政经/社会学/科技/文化教育
- 文章详情：
  - 段首显示原文URL（可点击跳转浏览器）
  - 英文原文分段显示（交替背景色）
  - 中文翻译分段显示
  - SelectionContainer支持长按选中文字
  - 底部固定"标记词汇"/"标记长难句"按钮（从剪贴板读取）
  - 标记的词汇在原文中橙色高亮
  - 重点词汇：点击加入英语题库（单词短语），长按进入删除模式
  - 长难句：点击加入英语题库（长难句），长按删除
  - Pin持久化保存文章
  - 标记数据持久化存储

---

## 八、制作音频 & 磨耳朵

### 8.1 制作音频
- 选择题库 → 设置题目数量/乱序/音色
- 音色选择：播音男声/清纯女声/干练女声/磁性男声
- 方案1：SiliconFlow CosyVoice2 TTS API
- 方案2：系统TTS实时朗读（API不可用时降级）
- 生成的音频保存到本地 `getExternalFilesDir/audio/`

### 8.2 磨耳朵
- 显示本地音频文件列表
- 支持导入外部音频文件
- 多选播放，顺序播放
- 底部固定操作栏：移除（隐藏）/ 删除（永久）/ 播放
- 播放状态实时显示

---

## 九、个人中心（我的Tab）

### 9.1 用户信息
- 头像（微信头像/默认图标）
- 昵称、角色
- 点击进入账号详情

### 9.2 目标分数
- 政治/英语/心理学专业综合 三科分数设定
- 总分自动计算

### 9.3 累计正确率
- 按题库维度显示（非科目维度），实时更新所有题库
- 每道题只算一次，取最新答题结果（不在错题本=答对）
- 百分比右对齐，标签不换行（widthIn(min=80.dp)）

### 9.4 功能菜单
| 菜单 | 功能 |
|------|------|
| 分享题库 | 调用系统分享，分享给微信联系人 |
| 分享APP | 分享GitHub Releases下载链接：https://github.com/yuyanpsy/psymap/releases |
| 数据备份 | 备份到Downloads/psymap_backup.json |
| 数据恢复 | 从Downloads或文件选择器恢复 |
| 版本信息 | 显示版本号、检查更新 |

---

## 十、数据备份与恢复

### 10.1 备份内容（v4）
| 数据 | 说明 |
|------|------|
| 题库 | 所有题库信息 |
| 题目 | 所有题目（含选项、标签、错误率） |
| 打卡记录 | 每日打卡数据 |
| 学习计划 | 每日目标设定 |
| 用户信息 | 昵称、头像、打卡天数 |
| 目标分数 | 三科目标分数 |
| API配置 | API Key、Base URL、模型名 |
| 英文泛读 | 文章列表、Pin状态 |
| 阅读标记 | 重点词汇、长难句 |
| 心理学知识 | 生成的论述题、Pin状态 |

### 10.2 备份方式
- MediaStore写入Downloads目录
- 恢复支持MediaStore查找和系统文件选择器

---

## 十一、版本更新

### 11.1 版本检查机制
- 版本检查：从 `https://raw.githubusercontent.com/yuyanpsy/psymap/main/version.json` 获取最新版本信息
- 比较逻辑：将远程 `versionName` 与当前内置版本号对比，不同则提示更新
- 点击更新后自动跳转浏览器下载 APK

### 11.2 version.json 格式
```json
{
  "versionCode": 7,
  "versionName": "0.0.7",
  "downloadUrl": "https://github.com/yuyanpsy/psymap/releases/download/v0.0.7/app-debug.apk",
  "changelog": "更新说明"
}
```
> 字段说明：`versionName`（版本号）、`downloadUrl`（APK下载直链）、`versionCode`（版本序号）、`changelog`（更新日志）

### 11.3 发布流程
1. 修改 `build.gradle.kts` 中 `versionCode` 和 `versionName`
2. 修改 `ProfilePage.kt` 中硬编码的 `currentVersion` 和显示版本号
3. 编译 APK：`./gradlew assembleDebug`
4. 更新根目录 `version.json`
5. 推送代码到 GitHub：`git push origin main --tags`
6. 在 GitHub Releases 页面创建新 release，上传 `app-debug.apk` 到 Assets
7. Release 页面：`https://github.com/yuyanpsy/psymap/releases`

### 11.4 分享下载链接
- 分享App功能使用链接：`https://github.com/yuyanpsy/psymap/releases`
- GitHub Pages 页面（备用）：`https://yuyanpsy.github.io/psymap`（需在 repo Settings > Pages 中启用）

---

## 十二、AI服务配置

| 配置项 | 默认值 |
|--------|--------|
| API Key | sk-ozgipwvoghexlmpzriaesynaypyqjszqdllemcqzxvaokzqr |
| Base URL | https://api.siliconflow.cn/v1 |
| OCR模型 | deepseek-ai/DeepSeek-OCR |
| 文本模型 | Qwen/Qwen2.5-72B-Instruct |
| TTS模型 | FunAudioLLM/CosyVoice2-0.5B |

---

## 十三、UI设计规范（MIUI风格）

| 元素 | 规范 |
|------|------|
| 主色调 | #FF8A00（橙色） |
| 背景色 | #F5F5F5（浅灰） |
| 卡片背景 | #FFFFFF（白色） |
| 卡片圆角 | 12dp |
| 卡片阴影 | 0dp（扁平风格） |
| 按钮圆角 | 10-12dp |
| 文字层级 | 标题16sp/正文14sp/辅助12sp/微小11sp |
| 图标尺寸 | 24dp（标准）/ 20dp（小）/ 16dp（微小） |
| 全屏页面 | Dialog + Scaffold + 手势返回支持 |
| 底部按钮 | 48dp底部padding适配导航栏 |

---

## 十四、技术架构

| 层级 | 技术 |
|------|------|
| UI框架 | Jetpack Compose + Material Design 3 |
| 架构 | MVVM（ViewModel + mutableStateOf） |
| 数据存储 | SharedPreferences（JSON序列化） |
| 网络 | OkHttp + Gson |
| 图片加载 | Coil |
| AI服务 | SiliconFlow API（DeepSeek-OCR + Qwen2.5） |
| TTS | SiliconFlow CosyVoice2 + Android系统TTS |
| 微信SDK | WXEntryActivity |
| 最低SDK | Android 8.0（API 26） |

---

## 十五、智选基金（FundPicker）模块

### 15.1 模块概述

智选基金是嵌入在羽言心理App中的独立功能模块，通过"我的"页面的"智选基金"入口进入。进入后界面完全切换为独立的5Tab导航体系，提供基金数据浏览、AI预测分析、模拟持仓等功能。

- 入口：我的 → 智选基金
- 技术实现：FullScreenDialog全屏覆盖，独立Scaffold + 底部5Tab
- 代码位置：`com.psymap.app.fundpicker` 包
- 数据源：东方财富/天天基金公开HTTP接口（实时数据）

### 15.2 功能架构

```
智选基金
├── 首页（AI推荐）
│   ├── 市场情绪指标（沪指/深指/创业板实时数据）
│   ├── AI精选Top10（按AI评分排序）
│   └── 热门板块轮动
├── 发现（基金市场）
│   ├── 搜索（名称/代码）
│   ├── 分类筛选（股票型/混合型/债券型/指数型/QDII/LOF）
│   └── 基金列表（实时净值+涨跌幅+AI评分）
├── 基金详情页
│   ├── 实时估值（盘中）/ 最新净值
│   ├── 基金信息（规模/成立日期/类型/经理/管理规模）
│   ├── 费率信息（管理费/托管费/销售服务费/申购费/赎回费/业绩基准）
│   ├── 净值走势图（7天/30天/3月/6月/1年/3年，可交互）
│   ├── AI预测面板（多周期预测+关键因子+置信度）
│   ├── 业绩表现（近7天~近1年涨跌幅）
│   ├── 前十大持仓
│   └── AI模型对比入口 → AI实验室
├── 持仓（模拟持仓）
│   ├── 模拟总资产/总收益
│   ├── 持仓明细（成本/现价/盈亏/仓位占比）
│   ├── 模拟买入/卖出
│   └── 交易记录
├── 自选（我的关注）
│   ├── 关注统计（涨/跌/平）
│   └── 自选基金列表
└── 我的
    ├── 风险偏好设置（保守/稳健/进取）
    ├── 默认时间维度设置
    ├── AI模型说明
    ├── 刷新数据
    ├── 意见反馈
    └── 返回羽言心理
```

### 15.3 数据源

| 数据 | 接口 | 说明 |
|------|------|------|
| 基金排行 | fund.eastmoney.com/data/rankhandler.aspx | 净值、涨跌幅、排名 |
| 实时估值 | fundgz.1234567.com.cn/js/{code}.js | 盘中实时估值 |
| 历史净值 | fundf10.eastmoney.com/F10DataApi.aspx | 历史净值走势 |
| 基金详情 | fund.eastmoney.com/pingzhongdata/{code}.js | 经理、持仓、资产配置 |
| 基金概况 | fundf10.eastmoney.com/jbgk_{code}.html | 完整费率、规模、基准 |
| 大盘指数 | push2.eastmoney.com/api/qt/ulist.np/get | 沪指/深指/创业板 |

### 15.4 AI实验室详细说明

AI实验室是智选基金的核心功能，允许用户对比8种预测算法的结果，并通过调节参数观察对预测的影响。

#### 15.4.1 算法说明

| 模型 | 类型 | 原理 | 来源 | 适用场景 |
|------|------|------|------|---------|
| LSTM | 深度学习 | 长短期记忆网络，基于历史净值序列捕捉时序依赖 | Hochreiter 1997 | 有明显趋势的基金 |
| GRU | 深度学习 | 门控循环单元，LSTM轻量变体，训练更快 | Cho 2014 | 短周期预测 |
| Transformer | 深度学习 | 自注意力机制，并行处理序列，捕捉长距离依赖 | Google 2017 | 市场结构性变化 |
| LightGBM | 机器学习 | 梯度提升树，综合技术指标+基本面+资金流多因子 | 微软 2017 | 多因子驱动的基金 |
| XGBoost | 机器学习 | 极端梯度提升，正则化更强防过拟合 | 陈天奇 2016 | 特征维度高的场景 |
| ARIMA | 统计模型 | 自回归移动平均，经典时序分析 | Box-Jenkins 1970 | 平稳序列（债券型） |
| 纯技术面 | 规则模型 | 仅使用MACD/RSI/布林带/KDJ等技术指标 | — | 短线交易参考 |
| 集成模型 | 融合模型 | 按用户设定权重加权融合多个模型的预测结果 | — | 综合判断（推荐） |

#### 15.4.2 参数说明

| 参数 | 范围 | 默认值 | 含义 | 调节效果 |
|------|------|--------|------|---------|
| LSTM回看窗口 | 20-180日 | 60日 | 模型参考过去多少天的净值数据 | 越大越关注长期趋势，概率趋向50% |
| 集成权重-LSTM | 0-100% | 30% | LSTM在集成预测中的占比 | 调高则集成结果更偏向时序趋势判断 |
| 集成权重-LightGBM | 0-100% | 30% | 多因子模型的占比 | 调高则更偏向综合因子判断 |
| 集成权重-Transformer | 0-100% | 15% | 自注意力模型的占比 | 调高则对结构性变化更敏感 |
| 集成权重-情绪分析 | 0-100% | 15% | NLP情绪模型的占比 | 调高则更受市场情绪影响 |
| 集成权重-ARIMA | 0-100% | 10% | 统计模型的占比 | 一般保持低权重 |

所有权重自动归一化到100%。

#### 15.4.3 结果解读

**对比结果表格：**
- 上涨概率：各模型独立预测该基金在选定周期内上涨的概率（0-100%）
- 置信度（星星）：模型对自己预测的把握程度（1-5星）
- 多数模型>70%且≥3星 → 信号较强
- 模型间分歧大 → 信号矛盾，建议观望

**历史回测表格：**
- 命中率：预测涨跌方向的正确比例（>55%有统计意义）
- 年化收益：按模型信号操作的年化回报率
- 最大回撤：期间最大亏损幅度（越小越好）
- 夏普比率：风险调整后收益（>1优秀，>1.5顶级）

#### 15.4.4 使用建议

1. 先用默认参数运行，看各模型基础判断
2. 如果多数模型概率>70%且置信度≥3星，信号较强
3. 调节参数观察集成模型概率的稳定性：波动小=信号可靠，波动大=不确定性高
4. 短线参考LSTM+纯技术面，中长线参考LightGBM+Transformer
5. 集成模型的结果通常最稳定，优先参考

⚠️ 当前版本AI预测基于历史涨跌数据的统计模型，不是真正训练的深度学习模型。后续迭代计划搭建Python后端训练真实模型。

### 15.5 代码结构

```
com.psymap.app.fundpicker/
├── FundModels.kt          # 数据模型（Fund, NavPoint, AiPrediction等）
├── FundApi.kt             # 东方财富API封装（排行/估值/净值/详情/概况）
├── FundRepository.kt      # 数据仓库（API调用+本地缓存+模拟持仓）
├── FundPickerViewModel.kt # ViewModel（状态管理+业务逻辑）
├── FundPickerApp.kt       # 主入口（Scaffold + 5Tab导航）
├── FundComponents.kt      # 共享UI组件（时间选择器/基金卡片/评分条）
├── FundHomePage.kt        # 首页（市场情绪+AI精选+板块轮动）
├── FundDiscoverPage.kt    # 发现页（搜索+分类筛选+基金列表）
├── FundDetailPage.kt      # 基金详情（估值+信息+费率+走势+AI预测+持仓）
├── PortfolioPage.kt       # 模拟持仓（总资产+持仓明细+交易记录）
├── FundFavoritesPage.kt   # 自选页（关注统计+自选列表）
├── AiLabPage.kt           # AI实验室（算法对比+参数调节+回测）
└── FundProfilePage.kt     # 我的（设置+免责声明）
```

### 15.6 AI预测后端（Python）

#### 15.6.1 架构

```
Mac本地训练 → 模型文件上传到GitHub → Render云端部署
                                        ↓
cron-job.org 每天16:35触发 → Render采集500只基金净值 → 模型预测 → 存Supabase
                                        ↓
Android App → /top10 获取TOP10 → /predict/{code} 实时预测任意基金
```

#### 15.6.2 模型训练

- 训练数据：200只基金×3年净值数据（26万+样本）
- 特征工程：38个技术指标（均线/RSI/MACD/布林带/波动率/动量/回撤等）
- 模型：GradientBoostingClassifier + RandomForestClassifier
- 验证：Walk-Forward 5折时序交叉验证
- 预测周期：7天/30天/90天三个模型

#### 15.6.3 模型效果

| 周期 | GB AUC | GB 准确率 | RF AUC | RF 准确率 |
|------|--------|----------|--------|----------|
| 7天  | 0.6528 | 60.48%   | 0.6339 | 58.82%   |
| 30天 | 0.6703 | 62.27%   | 0.6495 | 60.40%   |
| 90天 | 0.6961 | 63.58%   | 0.6619 | 60.99%   |

Top特征：60日波动率、20日波动率、RSI(28)、均线偏离度、布林带宽度

#### 15.6.4 云端部署

- 平台：Render.com（免费方案，512MB内存）
- API地址：https://fundpicker-api.onrender.com
- 代码仓库：https://github.com/yuyanpsy/fundpicker-api
- 定时任务：cron-job.org 每天16:35触发全量预测
- 持久化：Supabase fund_predictions表（Render休眠后自动恢复）

#### 15.6.5 API接口

| 接口 | 说明 |
|------|------|
| GET /trigger-update | 触发后台500只基金批量预测（异步） |
| GET /top10 | 获取预测概率最高的TOP10基金 |
| GET /predict/{code}?horizon=30 | 实时预测任意基金（自动获取数据） |
| GET /predict/{code}/all | 预测所有周期（7/30/90天） |
| GET /backtest?horizon=30 | 获取分档位回测胜率（前端展示用） |
| POST /run-backtest | 手动触发一次回测流程 |
| GET /health | 健康检查 |

#### 15.6.6 AI 预测算法详解

##### 预测目标

预测每只基金**未来 30 天净值上涨的概率**（0-100%）。二分类问题：30 天后净值比今天高 → 正样本。

##### 模型架构

- **GradientBoostingClassifier**（权重 60%）：逐步构建决策树，擅长非线性关系
- **RandomForestClassifier**（权重 40%）：300 棵独立决策树投票，抗过拟合
- **集成概率** = GB × 0.6 + RF × 0.4
- **置信度**：两模型预测结果一致程度（5⭐=完全一致，1⭐=严重分歧）

##### 训练方式

- Walk-Forward 5 折时序交叉验证（用过去预测未来，避免数据泄露）
- 训练数据：200+ 只基金 × 3 年净值（26 万+ 样本）
- 准确率：约 55-62%

##### 输入特征（38 个技术指标）

| 特征类别 | 具体指标 | 预测原理 |
|---------|---------|---------|
| 动量指标 | 5/10/20/60日动量 + 加速度 | 趋势延续性（动量效应） |
| RSI | RSI(6/14/28) | 超买超卖识别（均值回归） |
| MACD | MACD线/信号线/柱状图 | 趋势跟踪（金叉/死叉） |
| 布林带 | 上轨/下轨/带宽/位置 | 均值回归 + 突破预警 |
| 均线系统 | MA5/10/20/60 + 偏离度 + 交叉 | 趋势方向 + 强度 |
| 波动率 | 5/10/20/60日波动率 | 变盘预警（波动率聚集） |
| 最大回撤 | 20/60日回撤 | 风险度量 + 反弹概率 |
| 趋势一致性 | 5/20/60日方向一致比例 | 多周期确认 |
| 收益率 | 1/5/10/20/60日 + 对数收益 | 基础趋势信号 |

##### 预测结果输出（每只基金存入 Supabase）

```json
{
  "name": "基金名称",
  "probability": 78.3,        // AI 上涨概率 %
  "confidence": 4,            // 置信度 1-5
  "factors": [...],           // 关键因子（前3个）
  "nav_at_predict": 1.2345,   // 预测时净值（回测用）
  "sharpe": 1.61,             // 夏普比率（全量历史）
  "max_drawdown": 14.99,      // 最大回撤 %
  "positive_pct": 99.4        // 正收益概率 %
}
```

##### 基金基础数据来源

| 数据 | 来源 | 接口 |
|------|------|------|
| 基金排行（代码/名称/涨跌幅） | 东方财富 | `fund.eastmoney.com/data/rankhandler.aspx` |
| 净值走势（全量历史） | 东方财富 | `fund.eastmoney.com/pingzhongdata/{code}.js` |
| 实时估值 | 天天基金 | `fundgz.1234567.com.cn/js/{code}.js` |
| 大盘指数 + 现货黄金 | 新浪财经 | `hq.sinajs.cn/list=s_sh000001,...,hf_XAU` |
| 基金详情（经理/规模/费率/持仓） | 东方财富 | `fund.eastmoney.com/pingzhongdata/{code}.js` |

##### Cron 任务调度

| 任务名 | 频率 | 功能 |
|--------|------|------|
| `fundpicker-batch-predict` | 每 5 分钟 | 预测 500 只新基金（含风险指标），保存到 Supabase，更新 TOP10 |
| `fundpicker-daily-snapshot` | 每天 UTC 09:30（北京 17:30） | 存当天所有基金的预测概率+净值快照 |
| `fundpicker-daily-verify` | 每天 UTC 10:00（北京 18:00） | 对 30 天前快照回填实际涨跌 + 聚合档位胜率 |

##### Supabase 读写逻辑

| 表 | 写入时机 | 读取时机 |
|------|---------|---------|
| `fund_predictions.top10` | cron 每批完成后 PATCH | APP 启动 `loadCloudTop10()` |
| `fund_predictions.all_predictions` | cron 每批完成后 POST | APP 启动 `loadAiPredictions()` |
| `fund_prediction_snapshots` | 每天 17:30 | 30 天后对账 |
| `fund_prediction_backtest` | 每天 18:00 聚合 | APP 详情页展示 |
| `fund_picker_data` | 用户操作后 push | APP 启动时 pull |

##### 板块基金归类策略

- 23 个板块，每个板块有关键词列表
- 归类方式：基金名称包含任一关键词 → 归入该板块
- 按板块顺序匹配（"半导体"优先于"科技"）
- 板块基金列表从东方财富排行接口拉 3000 只，按关键词筛选
- 板块网格的"AI 最高"从 `all_predictions` 按名称关键词筛选

##### 首页 TOP10 显示逻辑

1. Cron 每次保存时从 Supabase **全量数据**选 TOP10
2. 优先选满足**全部金色条件**的基金，按 AI 降序取前 10
3. 金色不足 10 只时，用 AI 最高的补齐
4. 用 `PATCH` 单独更新 `top10` 字段（避免大 JSON 写入失败）
5. APP 启动时读 `top10` → `_topFunds` StateFlow → 首页展示

##### 金色基金显示逻辑

**条件（全部满足）：**
- AI 预测概率 ≥ 70%
- 置信度 ≥ 4
- 夏普比率 > 2
- 最大回撤 < 15%
- 正收益概率 > 80%

**展示规则：**
- 基金名称用金色（`#D4AF37`）粗体
- 所有页面统一从 `vm.aiPredictions[code]` 读取 5 个参数
- 判断逻辑统一在 `FundHeaderRow` 组件
- AI 数字本身统一用蓝色（不用金色）
- 红色/绿色只用于涨跌幅

##### 综合购买策略

| 条件 | 建议 |
|------|------|
| 满足全部金色条件 | 强烈推荐配置 |
| AI 60-70% + 夏普>1.0 + 回撤<20% | 可以考虑 |
| AI <50% 或 夏普<0.5 或 回撤>30% | 建议回避 |

##### APP 页面数据源对照

| 页面 | 涨跌幅来源 | AI/风险指标来源 | 金色判断来源 |
|------|-----------|---------------|------------|
| 首页 TOP10 | `enrichTopFundMonthChange` | `vm.aiPredictions` | `vm.aiPredictions` |
| 发现页搜索 | 东方财富排行 | `vm.aiPredictions` | `vm.aiPredictions` |
| 板块基金列表 | 东方财富排行 | `vm.aiPredictions` | `vm.aiPredictions` |
| 自选页 | Fund 对象 | `vm.aiPredictions` | `vm.aiPredictions` |
| 持仓页 | 实时估值 | `vm.aiPredictions` | `vm.aiPredictions` |
| 详情页 | navHistory | `vm.aiPredictions` | `vm.aiPredictions` |

#### 15.6.7 预测优化方案（2026-05 规划）

**已实施：**
- 回测闭环系统（每日快照 + 30天对账 + 档位胜率聚合）

**待实施（后端算法，用户无感知）：**

| 优化项 | 改动 | 预期提升 |
|--------|------|---------|
| Winsorize 极端值 | 单日涨跌 >10% 截尾处理 | 降噪，信号稳定性 +1-2% |
| 样本均衡 | `class_weight='balanced'` | 减少"单边乐观"偏差 |
| 低贡献特征剔除 | `feature_importances_ < 0.5%` 的剔除 | 减少过拟合 |
| Stacking 集成 | LogisticRegression 学最优 GB/RF 权重 | 整体准确率 +2-3% |
| 滚动训练窗口 | 只用最近 3 年数据训练 | 适配市场风格切换 |

#### 15.6.7 回测闭环系统

**目的：** 让用户看到"AI 说 70% 的基金，历史上真涨了多少次"

**数据流：**
```
每天 17:30 → daily_snapshot.py → 存当天所有基金的预测概率+当时净值
  → fund_prediction_snapshots 表（每天 ~2000 条）

30 天后 → daily_verify.py → 用最新净值对账
  → 回填 actual_nav_after / actual_return_pct / actual_up
  → 按档位聚合 → fund_prediction_backtest 表

APP 详情页 → 读 backtest 表 → 展示"历史参考"卡片
```

**前端展示效果（30天后）：**
```
📊 历史参考（同档位 70-80%）
过去 AI 给出此档位预测 1243 次，实际上涨 722 次，真实胜率 58.1%
平均实际涨跌 +3.2%
```

**数据库表：**
- `fund_prediction_snapshots`：每日快照（主键：snapshot_date + fund_code + horizon_days）
- `fund_prediction_backtest`：分档位胜率聚合（主键：horizon_days + bucket）

**Render Cron 任务：**
- `fundpicker-daily-snapshot`：每天 UTC 09:30（北京 17:30）
- `fundpicker-daily-verify`：每天 UTC 10:00（北京 18:00）

#### 15.6.8 用户购买决策参考

| AI 概率区间 | 含义 | 建议操作 |
|------------|------|---------|
| ≥80% | 强看涨信号 | 可重点关注，适合配置 |
| 70-80%（金色标注） | 偏看涨 | 适度参考，可少量配置 |
| 60-70% | 中性偏乐观 | 观望为主 |
| 50-60% | 中性 | 不建议买入 |
| <50% | 偏看跌 | 回避 |

**辅助判断指标：**
- 置信度（1-5⭐）：两个模型意见一致程度，≥3⭐更可信
- 历史胜率：同档位预测的真实历史表现（30天后开始显示）
- 关键因子：短期动量/RSI/MACD/趋势一致性/波动率/布林带位置
- 买入时AI → 当前AI：持仓后 AI 变化趋势（↑=看好加强，↓=看好减弱）

**购买决策流程：**
1. 首页 TOP10 → 快速发现 AI 评分最高的基金
2. 详情页 → 看 AI 概率 + 置信度 + 历史胜率
3. AI ≥70% + 置信度 ≥3⭐ + 历史胜率 >55% → 值得配置
4. 持仓后"当前 AI"持续下降 → 考虑减仓

**重要提醒：** 模型准确率约 55-62%，略优于随机。高概率+高置信度的组合信号更可靠。AI 无法预测黑天鹅事件，建议分散配置。

#### 15.6.6 数据流

```
每天16:35 → cron-job.org触发 /trigger-update
  → Render后台线程开始
  → 获取排行榜前500只基金代码
  → 逐只获取净值数据（pingzhongdata接口）
  → 计算38个技术指标特征
  → GradientBoosting + RandomForest预测
  → 每50只更新一次TOP10缓存
  → 全部完成后存到Supabase
  → 心跳保活（16:00-18:00每5分钟ping /top10）

用户打开App
  → 调用 /trigger-update（如果24小时内已更新则跳过）
  → 调用 /top10 获取TOP10显示在首页
  → 用户搜索基金 → 调用 /predict/{code} 实时预测
  → 用户进入详情页 → 调用 /predict/{code} 获取预测+因子
```

### 15.7 数据持久化

| 数据 | 存储位置 | 说明 |
|------|---------|------|
| 自选基金 | SharedPreferences + Supabase | 本地+云端双写 |
| 模拟持仓 | SharedPreferences + Supabase | 本地+云端双写 |
| 交易记录 | SharedPreferences + Supabase | 本地+云端双写 |
| 用户偏好 | SharedPreferences + Supabase | 风险偏好/默认周期 |
| AI预测结果 | Supabase fund_predictions表 | Render预测后存入 |
| 基金净值缓存 | Render内存 + 本地文件 | 每次预测时采集 |

### 15.8 后端代码结构

```
FundPicker/backend/
├── app/
│   ├── api_server.py          # FastAPI服务（/trigger-update, /top10, /predict, /backtest）
│   ├── data_collector.py      # 数据采集（东方财富pingzhongdata接口）
│   ├── feature_engineering.py # 38个技术指标特征工程
│   ├── model_trainer.py       # GB+RF模型训练（Walk-Forward验证）
│   ├── backtest.py            # 回测闭环（快照+对账+聚合，被api_server调用）
│   ├── daily_snapshot.py      # 独立cron：每日存预测快照到Supabase
│   ├── daily_verify.py        # 独立cron：30天后对账+聚合胜率
│   ├── batch_predict.py       # 批量预测导出JSON
│   ├── smart_collector.py     # 智能采集（按夏普/回撤筛选）
│   └── supabase_store.py      # Supabase持久化读写
├── sql/
│   └── create_backtest_tables.sql  # 回测表建表SQL
├── models/                    # 训练好的模型文件
│   ├── model_7d/              # 7天预测模型
│   ├── model_30d/             # 30天预测模型
│   └── model_90d/             # 90天预测模型
├── data/nav/                  # 基金净值数据（200只×CSV）
├── requirements.txt
├── Dockerfile
└── render.yaml
```

### 15.9 待开发功能

- [x] 行业板块分类（关键词匹配，23个板块）
- [x] 板块详情页（点击板块→显示该板块下的基金列表+排序）
- [x] 扩大预测覆盖（10000只基金批量预测，Supabase持久化）
- [x] 回测闭环系统（每日快照+30天对账+档位胜率聚合）
- [x] 指数走势页（上证/深证/创业板/现货黄金 K线图）
- [ ] 算法优化（Winsorize/样本均衡/Stacking/滚动窗口）
- [ ] 持仓AI预警（持仓基金趋势变化时推送通知）
- [ ] 深度学习模型（LSTM/Transformer，需要更大算力）


### 15.10 外部服务配置指南

#### 15.10.1 Supabase（云数据库）

项目地址：https://supabase.com/dashboard/project/edzsmjegnkrbedqpotgu

已创建的表：

| 表名 | 用途 | 创建方式 |
|------|------|---------|
| users | PsyMap用户（微信登录） | 已有 |
| fund_picker_data | FundPicker用户数据（自选/持仓/偏好） | SQL Editor建表 |
| fund_predictions | AI预测结果持久化（TOP10+全量预测） | SQL Editor建表 |
| fund_prediction_snapshots | 每日预测快照（回测对账用） | SQL Editor建表 |
| fund_prediction_backtest | 分档位胜率聚合（前端展示用） | SQL Editor建表 |

建表SQL（fund_picker_data）：
```sql
CREATE TABLE IF NOT EXISTS fund_picker_data (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    data JSONB DEFAULT '{}',
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE fund_picker_data ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage own fund data" ON fund_picker_data
    FOR ALL USING (true) WITH CHECK (true);
```

建表SQL（fund_predictions）：
```sql
CREATE TABLE IF NOT EXISTS fund_predictions (
    id TEXT PRIMARY KEY DEFAULT 'latest',
    top10 JSONB DEFAULT '[]',
    all_predictions JSONB DEFAULT '{}',
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE fund_predictions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read write" ON fund_predictions
    FOR ALL USING (true) WITH CHECK (true);
```

操作步骤：登录Supabase → 左侧SQL Editor → 粘贴SQL → Run

#### 15.10.2 Render（云端API服务）

控制台：https://dashboard.render.com
服务地址：https://fundpicker-api.onrender.com
代码仓库：https://github.com/yuyanpsy/fundpicker-api

配置参数：
- Name: fundpicker-api
- Language: Python 3
- Branch: main
- Build Command: `pip install -r requirements.txt`
- Start Command: `cd app && python api_server.py`
- Instance Type: Free（$0/月，512MB内存，0.1CPU）

部署步骤：
1. 登录 https://render.com（GitHub账号授权）
2. New → Web Service → 选择 fundpicker-api 仓库
3. 填写上述配置参数
4. 点 Deploy Web Service
5. 等待构建完成（约3-5分钟）
6. 访问 https://fundpicker-api.onrender.com 验证

注意事项：
- 免费方案15分钟无请求会休眠，首次请求需30-50秒唤醒
- 休眠后内存数据丢失，通过Supabase恢复
- 每次push到GitHub会自动重新部署
- 需要绑定信用卡（验证$1后退回，不实际收费）

更新模型步骤：
1. Mac上训练新模型：`cd ~/FundPicker/backend/app && python3 model_trainer.py`
2. 推送到GitHub：`cd ~/FundPicker/backend && git add -A && git commit -m "update models" && git push`
3. Render自动重新部署

#### 15.10.3 cron-job.org（免费定时任务）

控制台：https://cron-job.org（邮箱注册，免费）

已创建的定时任务：

| 任务名 | URL | Cron表达式 | 说明 |
|--------|-----|-----------|------|
| FundPicker 触发预测 | https://fundpicker-api.onrender.com/trigger-update | `35 8 * * 1-5` | 北京时间16:35，周一到周五 |
| FundPicker 保活 | https://fundpicker-api.onrender.com/top10 | `*/5 8-9 * * 1-5` | 北京时间16:00-17:59，每5分钟 |

注意：cron-job.org使用UTC时间，北京时间减8小时。

创建步骤：
1. 登录 https://cron-job.org
2. 点 Create cronjob
3. 填写Title、URL、Schedule（选Custom填Cron表达式）
4. 点 Create

工作流程：
```
每天16:35 → cron触发 /trigger-update → Render开始后台预测500只基金
16:00-18:00 → 每5分钟ping /top10 → 保持Render不休眠
约30-60分钟后 → 预测完成 → 结果存入Supabase
第二天早上 → 用户打开App → Render从Supabase加载结果 → 首页显示TOP10
```

手动触发预测（不等定时任务）：
```bash
curl https://fundpicker-api.onrender.com/trigger-update
```

查看预测进度：
```bash
curl https://fundpicker-api.onrender.com/top10
```
