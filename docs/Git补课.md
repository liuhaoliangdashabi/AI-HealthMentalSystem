# Git 补课：从三件套到敢改历史

> 起点：会 `add` / `commit` / `push`。
> 目标：出事的时候知道该敲哪一条，以及**为什么是那一条**。
>
> 本文所有例子都来自你这个仓库（60 次提交、main + rescue-local-work 两个分支、
> GitHub 远程、118 个编译产物误入版本库）。不是抽象教学。

---

## 0. 先定位：三件套覆盖了多少

`add` / `commit` / `push` 覆盖了**正常流程**的 90%。

Git 真正的价值在剩下的 10% —— **出事的时候**。新手和熟手的差距不在会不会提交，
而在于：

| 场景 | 新手 | 熟手 |
|---|---|---|
| 改错了想撤回 | 手动改回去 / 重新 clone | `git restore` |
| 提交信息写错 | 算了，将错就错 | `git commit --amend` |
| 提交里混进了不该提的文件 | 算了 | `git reset` 或 `git rm --cached` |
| 已经 push 了发现有问题 | 强推，把别人代码覆盖了 | `git revert` |
| 分支搞乱了 | 重新 clone 一遍 | `git reflog` 找回来 |
| 冲突 | 手忙脚乱删标记 | 读懂 `<<<<<<<` 再决定 |

**核心认知：Git 几乎不会真的丢数据。** 只要你 commit 过，哪怕 reset 掉了、
分支删了，`git reflog` 都能捞回来（默认保留 90 天）。
真正会丢的只有一种情况：**改完没 commit，然后被 `reset --hard` 或 `checkout` 覆盖了**。

---

## 1. 心智模型：快照 + 指针

这是整份文档最重要的一节。**搞懂这个，后面所有命令都能自己推出来。**

### 1.1 commit 是快照，不是 diff

很多人以为 Git 存的是「每次改了什么」。不是。

**每个 commit 存的是「那一刻整个项目的完整样子」。**

```
commit A:  [文件1 v1] [文件2 v1] [文件3 v1]
commit B:  [文件1 v2] [文件2 v1] [文件3 v1]     ← 只有文件1变了，但快照里三个文件都在
commit C:  [文件1 v2] [文件2 v2] [文件3 v1]
```

`git show` 看到的 diff 是**临时算出来的**（对比两个快照），不是存下来的东西。

**那为什么不占空间？** 因为 Git 存的是「内容对象」（blob），
相同内容只存一份。文件没变，两个 commit 就指向同一个 blob。所以「快照」和「省空间」不矛盾。

**这个认知有什么用**：
- 切分支是瞬间的（不用「应用一堆补丁」，直接换一套指针）
- `reset` 只是移动指针，文件内容该在哪还在哪
- 「找回某个文件的历史版本」= 去某个快照里把那个 blob 拿出来

### 1.2 分支只是一个指针

`main` 不是「一个文件夹」，是 `.git/refs/heads/main` 里的**一个 41 字节的文本文件**，
内容就是一个 commit 的 SHA-1：

```
$ cat .git/refs/heads/main
20a931de43b14666e150097accf1dfe04d9c346c
```

**创建分支 = 写一个 41 字节的文件。** 所以 Git 的分支「几乎不要钱」，
鼓励你随便开。这跟 SVN 那种「复制一份目录」完全不是一回事。

```
        ┌─────────┐
        │ commit C│ ← main 指向这里
        └────┬────┘
             │
        ┌────▼────┐
        │ commit B│
        └────┬────┘
             │
        ┌────▼────┐
        │ commit A│
        └─────────┘
```

新建 `feature` 分支，就是多一个文件指向 C。提交一次，`feature` 移到 D，`main` 还在 C。

### 1.3 HEAD 是「你在哪」

`.git/HEAD` 的内容通常是：

```
ref: refs/heads/main
```

意思是「HEAD 指向 main 分支，而 main 指向某个 commit」。**HEAD 最终指向一个 commit。**

两种情况：
- `HEAD → main → commit`：**在分支上**（正常状态）
- `HEAD → commit`（直接指向，不经过分支）：**detached HEAD**（分离头指针）

**detached HEAD 是什么时候出现的**：`git checkout <某个 commit 的 hash>` 的时候。
这时候你提交的 commit **不属于任何分支**，切走就「找不到了」（但 reflog 里还在）。

新手最容易在这里翻车：`git checkout 某个旧版本` 看看代码，改了点东西提交了，
然后 `git checkout main` —— 刚才那个提交就成孤儿了。

**记住：看历史用 `git log` / `git show`，不要 `checkout` 到旧 commit 上改东西。**
真要基于旧版本改，用 `git switch -c 新分支 <hash>`。

---

## 2. 三个区域：add 和 commit 到底在干什么

### 2.1 三个区域

```
┌──────────────┐   add    ┌──────────────┐  commit  ┌──────────────┐
│   工作区      │ ───────▶ │   暂存区      │ ───────▶ │   版本库      │
│ working tree │          │ index/stage  │          │  .git        │
│              │ ◀─────── │              │ ◀─────── │              │
└──────────────┘ restore  └──────────────┘  reset   └──────────────┘
```

| 区域 | 是什么 | 在哪 |
|---|---|---|
| **工作区** | 你能看见、能编辑的文件 | 项目目录 |
| **暂存区** | 「下次提交要包含什么」的清单 | `.git/index`（一个二进制文件） |
| **版本库** | 所有 commit 的历史 | `.git/objects` |

**关键：`git status` 显示的就是这三个区域之间的差异。**

```
Changes not staged for commit:   ← 工作区和暂存区不一样（改了但没 add）
Changes to be committed:         ← 暂存区和版本库不一样（add 了但没 commit）
```

### 2.2 为什么需要暂存区（不是多余设计）

很多人觉得「我改完直接 commit 不就行了，为什么要多一步 add」。

**因为一次提交应该只做一件事。**

假设你改了三处：
1. 修了个 bug
2. 顺手加了个功能
3. 又顺手格式化了整个文件

如果没有暂存区，这三处只能一起提交，commit 信息没法写清楚，
以后想「只回滚那个 bug 修复」也做不到。

有暂存区，你可以：

```bash
git add -p                  # 逐块确认要不要进这次提交（p = patch）
git commit -m "fix: 修复空指针"
git add .
git commit -m "feat: 新增导出功能"
```

**`git add -p` 是新手最该学的隐藏命令**，它让你按「代码块」而不是「整个文件」来暂存。

### 2.3 每条命令在区域间怎么移动

| 命令 | 方向 | 作用 |
|---|---|---|
| `git add <file>` | 工作区 → 暂存区 | 把改动放进「下次提交」 |
| `git commit` | 暂存区 → 版本库 | 生成快照 |
| `git restore <file>` | 暂存区 → 工作区 | **丢弃**工作区改动（危险） |
| `git restore --staged <file>` | 版本库 → 暂存区 | 撤销 `add`，工作区不动 |
| `git reset` | 版本库 → 暂存区 | 撤销 `commit`（见第 3 节） |
| `git checkout <file>` | 版本库 → 工作区 + 暂存区 | 老命令，现在推荐用 `restore` |

**注意 `restore` 和 `reset` 的分工**（这是 Git 2.23 才拆开的，老教程会混着讲）：

- **`restore`** 管文件内容（工作区 / 暂存区）
- **`reset`** 管提交历史（HEAD 指针）

---

## 3. 撤销：五个场景，五条命令

这是最实用的一节。**先看你在哪个阶段，再决定用哪条。**

```
你改错了
  │
  ├─ 还没 add ────────────────▶ git restore <file>
  │
  ├─ add 了，还没 commit ─────▶ git restore --staged <file>
  │
  ├─ commit 了，还没 push ────▶ git reset --soft / --mixed / --hard
  │                             或 git commit --amend（只想改信息/补文件）
  │
  ├─ 已经 push 了 ────────────▶ git revert <commit>     ← 别用 reset！
  │
  └─ 全乱套了，不知道在哪 ────▶ git reflog              ← 后悔药
```

### 3.1 还没 add：`git restore`

```bash
git restore src/main/java/xxx/UserService.java   # 单个文件
git restore .                                     # 全部
```

**它做了什么**：用暂存区的内容覆盖工作区。所以你的改动**直接消失**，没有确认。

**⚠️ 这是唯一会真正丢代码的常见命令**（因为改动还没进 Git）。敲之前先想一下。

### 3.2 add 了没 commit：`git restore --staged`

```bash
git restore --staged src/main/java/xxx/UserService.java
```

**它做了什么**：把暂存区重置成 HEAD 的样子，**工作区的改动还在**。

只是「把它从下次提交的清单里拿出去」，代码没动。

**记忆法**：`--staged` 就是「只动暂存区」。

### 3.3 commit 了没 push：`reset` 的三种模式

这是全 Git 最容易搞混的地方。三种模式**只差在「动哪几个区域」**：

| 模式 | HEAD | 暂存区 | 工作区 | 你的代码 |
|---|---|---|---|---|
| `--soft` | 后退 | **不动** | 不动 | 改动还在暂存区，可以直接重新 commit |
| `--mixed`（默认） | 后退 | 重置 | 不动 | 改动还在工作区，需要重新 add |
| `--hard` | 后退 | 重置 | **重置** | **改动没了** |

```bash
git reset --soft HEAD~1    # 撤销上一次 commit，改动回到暂存区
git reset HEAD~1           # = --mixed，撤销 commit + add，改动回到工作区
git reset --hard HEAD~1    # 撤销 commit + add + 改动，全没了（危险）
```

**典型用法**：

- 提交信息写错了 → `git commit --amend`（比 reset 更直接）
- 提交里多带了一个文件 → `git reset --soft HEAD~1`，然后重新 add 再 commit
- 提交做了一半想重来 → `git reset --mixed HEAD~1`

**`--hard` 的唯一正确心态**：只有当你确定「这些改动我不要了」才用。
真误操作了，先别慌，`git reflog` 还能找到那个 commit（见 3.5）。

### 3.4 已经 push 了：`git revert`

**这是最重要的一条纪律：已经推送到远程的提交，不要用 `reset` 撤销。**

原因：`reset` 是「把历史抹掉」，但远程的历史已经被别人拉走了。
你抹掉后要推上去，只能用 `push --force`，这会把别人的提交一起冲掉。

**正确做法是 `revert`：生成一个「反向提交」，把之前的改动抵消掉。**

```bash
git revert <commit-hash>
```

```
提交历史：
A ── B ── C ── D
              │
              └─ D 是错误提交

git revert D 之后：
A ── B ── C ── D ── D'
                    └─ D' 的内容 = 把 D 的改动反过来做一遍
```

**历史是往前走的，不是往回退的。** 别人 pull 下来不会有任何冲突。

| | `reset` | `revert` |
|---|---|---|
| 历史 | 被改写（commit 消失） | 追加一条新 commit |
| 已 push 的能用吗 | ❌ 要 force push | ✅ 安全 |
| 适用场景 | 本地、还没推送 | 已经推送、多人协作 |

### 3.5 全乱套了：`git reflog`

**这是 Git 的后悔药，每个新手都该先学会这一条。**

```bash
git reflog
```

输出：

```
20a931d (HEAD -> main) HEAD@{0}: commit: [all]新的一天...
a601cf6 HEAD@{1}: commit: (all)原有项目融入Redis成功
ce56194 HEAD@{2}: checkout: moving from main to rescue-local-work
057c183 HEAD@{3}: reset: moving to HEAD~1        ← 这里被 reset 掉了
```

**它记录了 HEAD 的每一次移动**，包括 commit、reset、checkout、merge、rebase。

误 `reset --hard` 之后怎么救：

```bash
git reflog                        # 找到 reset 之前那个 commit 的 hash
git reset --hard 057c183          # 回到那个 commit
```

**注意**：reflog 只记录 HEAD 的移动。如果 commit 从来没被 HEAD 指向过（比如在别的分支上），
reflog 里找不到，得用 `git fsck --lost-found`。

reflog 默认保留 90 天（`gc.reflogExpire`），够用了。

---

## 4. 分支与合并

### 4.1 常用操作

```bash
git branch                    # 列出本地分支
git branch -a                 # 包括远程分支
git switch -c feature-x       # 新建并切换（推荐，比 checkout -b 语义清楚）
git switch main               # 切换
git branch -d feature-x       # 删除已合并的分支
git branch -D feature-x       # 强制删除（没合并也删）
git branch -m old new         # 重命名
```

**`switch` 和 `checkout` 的区别**：`checkout` 一个命令干了太多事（切分支、还原文件、detached HEAD），
Git 2.23 把它拆成了 `switch`（切分支）和 `restore`（还原文件）。**新代码用新的。**

### 4.2 merge 的两种情况

**情况一：fast-forward（快进）**

如果 `main` 自从分叉后**没有任何新提交**，合并就是把 `main` 的指针直接挪过去：

```
合并前：                     合并后：
A ── B ── C (main)           A ── B ── C ── D ── E (main, feature)
           \
            D ── E (feature)
```

**不产生 merge commit**，历史是一条直线。

**情况二：三方合并（three-way merge）**

如果两个分支**都有新提交**，Git 会找一个共同祖先，把两边的改动合起来，生成一个 merge commit：

```
      D ── E (feature)
     /       \
A ── B ── C ── M (main)
                ↑
             merge commit，有两个父提交
```

**`--no-ff` 参数**：即使能快进，也强制生成 merge commit，保留「这里合并过一个分支」的信息。
团队协作时常用，因为能看出功能边界。

### 4.3 rebase 在干什么

`rebase` 的字面意思是「换基底」。它把你分支上的提交**一个个摘下来，重新应用到目标分支的最新提交上**。

```
rebase 前：
      D ── E (feature)
     /
A ── B ── C (main)

git switch feature
git rebase main

rebase 后：
A ── B ── C ── D' ── E' (feature, main)
```

**看起来像「把分叉拉直了」。**

**但要注意：D' 和 E' 是新的 commit，hash 变了**（因为父提交变了，内容一样但身份不一样）。

### 4.4 merge vs rebase 怎么选

| | merge | rebase |
|---|---|---|
| 历史形状 | 保留分叉，有 merge commit | 一条直线 |
| commit hash | 不变 | **变了** |
| 冲突处理 | 一次性解决 | 可能每个提交都要解 |
| 适合 | 合并到公共分支（main） | 自己的功能分支跟上 main 的最新进展 |

**铁律：不要 rebase 已经 push 且别人在用的分支。**

因为 hash 变了，别人本地的历史和远程对不上，会变成一团乱麻。
**自己的分支随便 rebase，公共分支永远只 merge。**

### 4.5 冲突怎么读

冲突时文件里会出现：

```
<<<<<<< HEAD
你当前分支的内容
=======
要合并进来的内容
>>>>>>> feature-x
```

三个标记的含义：
- `<<<<<<< HEAD` 到 `=======` 之间：**你所在分支**的版本
- `=======` 到 `>>>>>>>` 之间：**要合进来**的版本

**解决步骤**：

1. 打开文件，手动改成你想要的样子（三个标记全删掉）
2. `git add <file>` —— 告诉 Git「这个文件我处理好了」
3. `git commit`（merge 时）或 `git rebase --continue`（rebase 时）

**想放弃这次合并**：

```bash
git merge --abort      # 回到合并前
git rebase --abort     # 回到 rebase 前
```

**一个实用技巧**：`git diff --name-only --diff-filter=U` 列出所有冲突文件。

---

## 5. 远程：origin 到底是什么

### 5.1 origin 只是一个名字

```bash
$ git remote -v
origin  git@github.com:liuhaoliangdashabi/AI-HealthMentalSystem.git (fetch)
origin  git@github.com:liuhaoliangdashabi/AI-HealthMentalSystem.git (push)
```

`origin` 是**远程仓库的默认别名**，没有魔法。你完全可以叫它 `github` 或 `upstream`。

### 5.2 `origin/main` 是「本地缓存的远程状态」

这是最容易误解的一点。

**`origin/main` 不是你电脑上的一个分支，也不实时等于 GitHub 上的 main。**

它是「**你上次和远程通信时，远程 main 在哪**」的本地记录。

```
GitHub 上的 main:        A ── B ── C ── D
你本地的 origin/main:    A ── B ── C        ← 上次 fetch 时的样子
你本地的 main:           A ── B ── C
```

别人推了 D 上去，你本地**不会自动知道**，直到你 `fetch`。

### 5.3 fetch vs pull

| 命令 | 做什么 | 会改你的工作区吗 |
|---|---|---|
| `git fetch` | 只更新 `origin/main` 这个记录 | ❌ 不会 |
| `git pull` | `fetch` + `merge`（把远程改动合到当前分支） | ✅ 会 |

**`pull` = `fetch` + `merge`**（如果你的配置是 rebase，那就是 `fetch` + `rebase`）。

**推荐习惯**：先 `fetch` 看一眼，再决定 merge 还是 rebase：

```bash
git fetch
git log HEAD..origin/main --oneline    # 看看远程多了什么
git diff HEAD origin/main              # 看看改了什么
git merge origin/main                  # 确认没问题再合
```

`git pull` 一把梭适合「我一个人开发」的场景。多人协作时先看再合更安全。

### 5.4 upstream：`-u` 到底设了什么

```bash
git push -u origin main
```

`-u`（`--set-upstream`）在 `.git/config` 里写两行：

```ini
[branch "main"]
    remote = origin
    merge = refs/heads/main
```

意思是「本地 main 跟踪远程 origin/main」。设了之后：

- `git push` 不用再写 `origin main`
- `git pull` 知道该拉哪个
- `git status` 会告诉你「领先几个提交、落后几个提交」

**新分支第一次 push 一定要带 `-u`**，否则 Git 不知道该推到哪。

---

## 6. 你仓库里的两个真实问题

这两个不是理论，是刚才在你项目里查出来的。

### 6.1 118 个编译产物在版本库里

```
被 git 跟踪的文件总数：316
其中 ai-springboot/target/ 下的：118
其中 .class 文件：114
```

**但你的 `.gitignore` 里明明写了：**

```
target/
*.class
```

**为什么没生效？**

因为 **`.gitignore` 只对「未被跟踪」的文件生效**。已经进了版本库的文件，加 ignore 也没用。

时间线还原：

```
4d948b8  收官，完结                          ← target/ 在这一版被提交了
b2f1656  demo: 接入 Redis 并练习...          ← .gitignore 在这一版才加
```

先提交，后加 ignore，所以那 118 个文件一直留在库里。

**后果**：
- 仓库体积变大（每次编译都产生新的 class，diff 里全是二进制噪音）
- 每次 `git status` 一堆变化，真正的代码改动被淹没
- 别人 clone 下来一堆过期编译产物

**修法**（`--cached` 是关键，它只从版本库删除，不删本地文件）：

```bash
git rm -r --cached ai-springboot/target
git commit -m "chore: 移除误提交的编译产物"
```

**验证**：

```bash
git ls-files | grep -c "target/"     # 应该是 0
```

**这是新手最常踩的坑之一，面试也可能问：「.gitignore 对已跟踪文件有效吗？」**

### 6.2 commit 信息看不出改了什么

你现在的提交信息：

```
20a931d [all]新的一天，新的开始（原有项目融入Redis成功）
a601cf6 (all)原有项目融入Redis成功
057c183 收尾，明天总决战
4fec1cb 先交一下，就差那几个了
49fd194 7.2完成
```

**问题不在「不正式」，在于「三个月后你自己也看不懂」。**

- `收尾，明天总决战` —— 收的什么尾？
- `[all]` 和 `(all)` 两种写法混用 —— 如果是想标「改了哪些模块」，那不如写清楚
- 没法按类型过滤（想知道「这周修了几个 bug」，做不到）

**不需要上 Conventional Commits 那套严格的规范，但至少做到三点**：

1. **第一行说清「做了什么」**，不超过 50 字
2. **能分类就分类**（`feat` / `fix` / `docs` / `refactor` / `chore`）
3. **为什么改写在正文**，别写在标题里

对比：

```
❌ 收尾，明天总决战

✅ fix: 修复情绪日志表格列错位
   根因是 status-panel 用了 position:fixed + 写死 top:120px，
   页面顶部有导航栏导致算错位置。改成 flex 文档流。
```

**好处是实打实的**：

```bash
git log --oneline --grep="fix"        # 只看修复
git log --oneline --since="1 week ago"
git log --oneline -- ai-vue/src/      # 只看前端改动
```

**如果哪天想回滚「某个功能」，靠 `收尾，明天总决战` 是找不回来的。**

---

## 7. 高频事故速查表

| 我干了什么 | 怎么救 |
|---|---|
| 改乱了，想全丢掉 | `git restore .`（⚠️ 改动会没） |
| add 错了文件 | `git restore --staged <file>` |
| commit 信息写错（没 push） | `git commit --amend -m "新信息"` |
| commit 漏了文件（没 push） | `git add <file> && git commit --amend --no-edit` |
| commit 多了文件（没 push） | `git reset --soft HEAD~1` → 重新 add → commit |
| 提交到错误的分支（没 push） | `git reset --hard HEAD~1` → `git switch 正确分支` → 重新提交 |
| 已经 push 了才发现错了 | `git revert <hash>`（**别 reset**） |
| 误 `reset --hard` | `git reflog` 找到 hash → `git reset --hard <hash>` |
| 误删分支 | `git reflog` 找到分支最后一个 commit → `git switch -c 分支名 <hash>` |
| 想看某次提交改了什么 | `git show <hash>` |
| 想看某一行是谁改的 | `git blame <file>` |
| 想看某个文件的历史 | `git log -p -- <file>` |
| 想临时存一下改动去切分支 | `git stash` → 切走 → `git stash pop` |
| 冲突了想放弃合并 | `git merge --abort` |
| rebase 到一半乱了 | `git rebase --abort` |
| 误把密码提交了 | 改密码 → `git rm --cached 文件` → 加 ignore → commit（历史里的旧密码仍在，要彻底清得用 `filter-repo`） |

**最后一条要特别注意**：`git rm --cached` 只是让以后不再跟踪，
**历史提交里的密码还在**，任何人 clone 都能翻到。真正的处理是**立刻改密码/换 key**。

---

## 8. 十条命令覆盖 90% 场景

背这十条，比背 60 条命令有用：

```bash
git status -sb              # 我在哪个分支、改了什么、领先/落后远程几个
git diff                    # 工作区 vs 暂存区
git diff --staged           # 暂存区 vs 上次提交
git log --oneline --graph --all    # 图形化看历史（含所有分支）
git restore <file>          # 丢弃工作区改动
git restore --staged <file> # 撤销 add
git commit --amend          # 改最后一次提交
git reset --soft HEAD~1     # 撤销最后一次提交，改动保留
git reflog                  # 后悔药
git switch -c <branch>      # 开新分支
```

**加两个「看一眼就明白」的**：

```bash
git log --oneline --graph --all --decorate    # 完整历史图
git show <hash> --stat                        # 某次提交改了哪些文件
```

---

## 9. 三条最该记住的原则

**① 已推送的历史不要改写。**
`reset` / `rebase` / `commit --amend` 都只用在「还没 push」的提交上。
push 之后的错误，用 `revert` 追加一条反向提交。

**② 提交前先 `git status -sb` 和 `git diff --staged`。**
看一眼「我要提交的到底是什么」。90% 的误提交都能在这一步拦住。

**③ 慌了就 `git reflog`。**
Git 几乎不会真的丢数据。先别乱敲 `reset --hard`，先看 reflog。

---

## 10. Git 到底是什么数据结构

**先说结论：不是链表，是「有向无环图」（DAG）。**

「HEAD 像链表的头指针」这个直觉对了一半 —— HEAD 确实是入口，commit 之间也确实靠指针串起来。
但漏了两个关键点：**指针是单向的**，而且**可以分叉**。

### 10.1 一个 commit 里到底存了什么

`git cat-file -p <hash>` 能看到原始内容：

```
tree 4b825dc642cb6eb9a060e54bf8d69288fbee4904
parent 20a931de43b14666e150097accf1dfe04d9c346c
author  HP <hp@example.com> 1760000000 +0800
committer HP <hp@example.com> 1760000000 +0800

fix: 修复表格列错位
```

只有四个字段：

| 字段 | 含义 |
|---|---|
| `tree` | 指向一个目录树对象（那一刻整个项目的快照） |
| `parent` | 指向父提交。**0 个**（第一个提交）/ **1 个**（普通）/ **2 个以上**（merge） |
| `author` / `committer` | 作者 / 提交者（rebase 时两者会不同） |
| message | 提交信息 |

**注意 `parent` 是一个字段，不是两个。** 没有 `child`。

### 10.2 三个「不是链表」的理由

**① 单向**

commit 只知道自己的父亲，不知道自己的儿子。

```
A ◀── B ◀── C ◀── D        （箭头 = parent 指针）
```

所以你能从 D 一路走回 A（`git log` 干的就是这个），
但**没法从 A 走到 D** —— Git 根本没存「谁是我的孩子」。

**这解释了一个常见困惑**：「这个提交之后有哪些提交？」这个问题 Git 答得很费劲，
因为要遍历所有分支的 HEAD，各自往回走，看谁的路上经过它。

```bash
git branch --contains <commit>    # 就是干这个的，分支多了会慢
```

**② 可以分叉**

一个 commit 可以有多个孩子：

```
        ┌── D
A ── B ─┼── C
        └── F
```

B 有三个孩子。这就是「开了三个分支」的样子。

**③ 可以合并**

一个 commit 可以有多个父亲：

```
      D ── E
     /       \
A ── B ── C ── M
```

M 的 `parent` 字段有两行（E 和 C）。**这就是 merge commit 和普通 commit 的唯一区别。**

### 10.3 这个结构解释了一堆现象

| 现象 | 原因 |
|---|---|
| `git log` 很快 | 从 HEAD 沿 parent 往回走，单向遍历 |
| 切分支是瞬间的 | 只换一个指针，不动任何文件 |
| **rebase 之后 hash 变了** | 父提交变了 → commit 内容变了 → SHA-1 变了 |
| 删分支不删 commit | 分支只是指针，commit 对象还在，`reflog` 能找到 |
| 「某提交之后有什么」很慢 | 没有 child 指针，必须遍历所有 ref 反查 |
| 不可能出现循环 | 无环图，A 不可能是自己的祖先 |

**重点说 rebase 为什么改 hash。**

Git 的 hash 是**对 commit 的全部内容算的**（tree hash + parent hash + author + message）。

```
原始：parent = C  →  hash = a1b2c3
rebase 到新的 C'：parent = C'  →  输入变了  →  hash = d4e5f6
```

所以 `D` 变成 `D'`，**不是 Git 故意换个名字，是内容真的变了**。

这就是「内容寻址」：**hash 不是 ID，是内容的指纹**。
内容一样，hash 必然一样；内容差一个字节，hash 完全不同。

**推论**：同一个 commit 在两个仓库里的 hash 一定相同（内容相同），
除非它经过了 rebase / amend / cherry-pick（这些都会改变 parent 或内容）。

### 10.4 无环是什么意思

**不可能 A 是 B 的祖先，同时 B 又是 A 的祖先。**

否则 `git log` 会死循环。Git 靠「commit 只能指向更早的提交」保证这一点 ——
你不能创建一个「父提交在未来」的 commit。

想验证的话：`git merge-base --is-ancestor A B`，返回 0 表示 A 是 B 的祖先。

---

## 11. 业界命名规范

这一节讲三件事：**提交信息**、**分支名**、**版本号**。

### 11.1 提交信息：Conventional Commits

这是目前事实上的行业标准（Angular 团队提出，被 commitlint / semantic-release 等工具广泛采用）。

**格式**：

```
<type>(<scope>): <subject>
                        ← 空行，必须有
<body>
                        ← 空行
<footer>
```

**type 有 11 个，背下来就够用**：

| type | 用途 | 例子 |
|---|---|---|
| `feat` | 新功能 | `feat: 新增每日签到` |
| `fix` | 修 bug | `fix: 修复情绪日志表格列错位` |
| `docs` | 只改文档 | `docs: 补充 Git 补课文档` |
| `style` | 格式（空格、分号、缩进），**不影响逻辑** | `style: 统一缩进为 4 空格` |
| `refactor` | 重构（既不修 bug 也不加功能） | `refactor: 抽取分页参数校验` |
| `perf` | 性能优化 | `perf: 热门文章改用 ZUNIONSTORE 预热` |
| `test` | 加测试 / 改测试 | `test: 补充签到接口的边界用例` |
| `build` | 构建系统、依赖变更 | `build: 升级 Spring Boot 到 3.5.15` |
| `ci` | CI 配置 | `ci: 增加 Maven 缓存` |
| `chore` | 杂务（不改 src 和 test） | `chore: 移除误提交的编译产物` |
| `revert` | 回滚某次提交 | `revert: 回滚 feat: 新增导出` |

**`scope` 可选**，写模块名，让人一眼知道影响范围：

```
feat(auth): 新增短信验证码登录
fix(redis): 修复签到 Bitmap 读取错误
refactor(comment): 压缩冗余注释
```

**`subject` 的五条规则**：

1. **祈使句**：写 `add`，不写 `added` / `adds`（想象「这个提交将会……」）
2. **50 字符以内**（中文约 25 字）
3. **不加句号**
4. **首字母小写**（英文）；中文直接写，不加句号
5. **说「做了什么」，不说「为什么」** —— 为什么写在 body

**`body` 写什么**：写**动机**，不写**细节**。细节看 diff 就够了。

```
fix(emotion-diary): 修复表格列错位

status-panel 用了 position:fixed + 写死 top:120px，
但页面顶部有导航栏，实际位置被算错。改成 flex 文档流。
```

**`footer` 写什么**：

```
BREAKING CHANGE: 移除 /api/v1/user 接口，改用 /api/v2/user
Closes #42
```

**为什么值得遵守**（不是形式主义）：

```bash
git log --oneline --grep="^fix"          # 这周修了哪些 bug
git log --oneline --grep="^feat(redis)"  # Redis 相关加了什么功能
```

配合工具还能自动化：
- **commitlint**：提交时校验格式，不符合直接拒绝
- **semantic-release**：根据 type 自动算版本号、自动生成 CHANGELOG、自动打 tag
- **husky**：Git hook 管理，提交前自动跑 lint

**你现在的写法**对比：

```
❌ 收尾，明天总决战
❌ [all]新的一天，新的开始（原有项目融入Redis成功）

✅ feat(redis): 完成第 7 关全部场景接入
✅ chore: 移除误提交的 118 个编译产物
```

### 11.2 分支命名

先看三种主流工作流，**别一上来就上最重的那个**。

| 工作流 | 分支结构 | 适合 |
|---|---|---|
| **Git Flow** | `main` + `develop` + `feature/*` + `release/*` + `hotfix/*` | 有版本发布周期的产品（客户端、SDK） |
| **GitHub Flow** | 只有 `main` + 短命的 `feature/*` | **绝大多数 Web 项目、个人项目** |
| **Trunk-Based** | 几乎只有 `main`，分支活不过一天 | 有强 CI/CD 的团队（Google、Netflix） |

**Git Flow 的问题**：`develop` 和 `main` 长期分叉，合并时冲突巨大，对一个人开发来说是纯负担。

**推荐你用 GitHub Flow**：`main` 永远可发布，每个功能开一个分支，做完合回去。

**命名格式**：

```
<type>/<简短描述>
<type>/<issue号>-<简短描述>
```

| 前缀 | 用途 | 例子 |
|---|---|---|
| `feature/` | 新功能 | `feature/sign-in` |
| `fix/` | 普通 bug | `fix/diary-table-layout` |
| `hotfix/` | 线上紧急修复 | `hotfix/login-500` |
| `refactor/` | 重构 | `refactor/comment-cleanup` |
| `docs/` | 文档 | `docs/git-guide` |
| `chore/` | 杂务 | `chore/remove-target` |

**六条命名规则**：

1. **全小写**：`feature/user-login`，不是 `Feature/UserLogin`
2. **用连字符**，不用下划线、不用驼峰：`fix/table-layout` 而不是 `fix/table_layout`
3. **2~4 个词**，能看懂就行，别写作文
4. **带 issue 号**（有的话）：`feature/42-user-login`
5. **不用中文**（跨平台、命令行转义都会出问题）
6. **不要用这些烂名字**：`test`、`temp`、`new`、`dev2`、`fix`、`aaa`、`我的分支`

**反例**：

```
❌ test              → 谁的测试？测什么？
❌ fix               → 修什么？
❌ new-feature       → 什么功能？
❌ zhangsan          → 用人名当分支名，人走了分支就没人敢删
❌ feature/用户登录   → 中文在某些终端/CI 里会乱码
```

**好名字的标准**：三个月后看到 `feature/sign-in`，你还知道它要干什么。

### 11.3 版本号：SemVer（语义化版本）

格式固定三位：

```
v<major>.<minor>.<patch>
   │       │       └─ 修 bug，向下兼容
   │       └───────── 加功能，向下兼容
   └───────────────── 不兼容的变更（breaking change）
```

| 变化 | 版本怎么变 | 例子 |
|---|---|---|
| 修了个 bug | patch +1 | `1.4.2` → `1.4.3` |
| 加了个功能 | minor +1，patch 归零 | `1.4.3` → `1.5.0` |
| 改了接口签名 / 删了接口 | major +1，后面归零 | `1.5.0` → `2.0.0` |

**0.x.y 的特殊含义**：`0.1.0` 表示「还在开发中，API 随时可能变」。
一旦发布 `1.0.0`，就意味着「对外承诺稳定」。

**预发布标签**（写在 patch 后面，用连字符）：

```
1.0.0-alpha        内部测试
1.0.0-alpha.1      内部测试第 1 版
1.0.0-beta.3       公测第 3 版
1.0.0-rc.1         候选发布（Release Candidate）
```

**排序规则**：`1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-beta < 1.0.0-rc.1 < 1.0.0`

**两种 tag，用附注的**：

```bash
git tag v1.0.0                          # 轻量 tag：只是一个指针
git tag -a v1.0.0 -m "首个正式版本"      # 附注 tag：有作者、时间、说明（推荐）
git tag -a v1.1.0 -m "新增签到功能" 20a931d   # 给历史提交补打 tag
```

**为什么用附注 tag**：`git show v1.0.0` 能看到谁打的、什么时候、为什么。
轻量 tag 只有一个 hash，什么信息都没有。

```bash
git tag                    # 列出所有 tag
git tag -l "v1.*"          # 按模式过滤
git push origin v1.0.0     # 推单个 tag（tag 不会跟着 push 自动上去）
git push --tags            # 推所有 tag
git tag -d v1.0.0          # 删本地 tag
git push origin :refs/tags/v1.0.0   # 删远程 tag
```

**⚠️ tag 和分支不一样**：tag 是**不可变**的（打上去就不该改）。
如果发错版本了，正确做法是**打一个新版本**（`v1.0.1`），而不是删掉重打 ——
因为别人可能已经基于 `v1.0.0` 拉代码了。

### 11.4 落到你这个项目上

现状：`main` + `rescue-local-work`，60 次提交，commit 信息自由发挥。

**建议（一个人开发，别搞太复杂）**：

**① 用 GitHub Flow，不要 Git Flow**

```bash
# 每个功能开一个分支，做完合回 main
git switch -c feature/sign-in
# ... 改代码 ...
git switch main
git merge --no-ff feature/sign-in
git branch -d feature/sign-in
```

`--no-ff` 强制生成 merge commit，这样 `git log --graph` 能看出「这是一个功能分支」。

**② 提交信息用「简化版」Conventional Commits**

不用严格到 `scope` 和 `footer`，但 **type 必须有**：

```
feat: 新增每日签到
fix: 修复签到 Bitmap 读取错误
docs: 补充 Git 补课文档
refactor: 压缩冗余注释
chore: 移除误提交的编译产物
```

**③ 里程碑打 tag**

你现在有 60 次提交但一个 tag 都没有，回看「Redis 接入完成时是什么样」只能靠翻 log。

```bash
git tag -a v1.0.0 -m "Redis 接入完成，13 个场景全部上线"
git push origin v1.0.0
```

以后想回到那个版本：`git switch -c old-version v1.0.0`。

**④ `rescue-local-work` 分支处理掉**

```bash
git log --oneline main..rescue-local-work    # 看它比 main 多什么
git show ce56194                             # 看那个提交改了什么
# 有用 → git merge rescue-local-work
# 没用 → git branch -d rescue-local-work
```

留着「不知道干什么的分支」比删掉更危险 —— 半年后你不敢动它，它就成了技术债。

---

## 附：下一步练什么

按这个顺序练，每个都亲手做一遍（在测试分支上做，别在 main 上）：

1. `git switch -c practice`，改点东西，`git restore` 丢掉
2. 改点东西 add 了，`git restore --staged` 撤回来
3. commit 了，`git reset --soft HEAD~1` 再重新提交
4. commit 了，`git commit --amend` 改信息
5. `git reset --hard HEAD~1`，然后 `git reflog` 救回来
6. 开两个分支改同一个文件的同一行，制造冲突，手动解决
7. 一个分支用 merge，另一个用 rebase，对比 `git log --graph` 的形状
8. `git stash` 存改动，切分支，再 `stash pop`

**练完这 8 个，日常的 Git 就没有能难住你的了。**
