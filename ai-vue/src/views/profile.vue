<template>
    <div class="profile-page">
        <!-- ================= 左栏：个人信息（滚动时固定） ================= -->
        <aside class="sidebar">
            <div class="profile-card">
                <div class="avatar-wrap">
                    <el-avatar :size="92" :src="avatarUrl" class="avatar">
                        {{ initial }}
                    </el-avatar>
                    <!-- 空闲：hover 出现"更换"，点一下选图 -->
                    <div class="avatar-mask" v-if="!uploading" @click="pickFile" title="点击更换头像">
                        更换
                    </div>
                    <!-- 上传中：转圈 + 取消。选错图了不用干等到超时 -->
                    <div class="avatar-mask progress" v-else>
                        <el-icon class="is-loading spin"><Loading /></el-icon>
                        <button type="button" class="cancel-btn" @click.stop="cancelUpload">取消</button>
                    </div>
                </div>
                <input ref="fileInput" type="file" accept="image/*" class="hidden-input"
                    @change="onFileChange">

                <h2 class="nickname">{{ user.nickname || user.username || '未登录' }}</h2>
                <p class="username">@{{ user.username || 'anonymous' }}</p>
                <p class="bio">{{ bio }}</p>

                <ul class="meta-list">
                    <li v-if="user.email">
                        <el-icon><Message /></el-icon>
                        <span>{{ user.email }}</span>
                    </li>
                    <li v-if="user.phone">
                        <el-icon><Iphone /></el-icon>
                        <span>{{ user.phone }}</span>
                    </li>
                    <li v-if="user.birthday">
                        <el-icon><Present /></el-icon>
                        <span>{{ user.birthday }}</span>
                    </li>
                    <li v-if="joinedAt">
                        <el-icon><Calendar /></el-icon>
                        <span>加入于 {{ joinedAt }}</span>
                    </li>
                </ul>

                <el-button class="edit-btn" @click="openEdit">
                    <el-icon><EditPen /></el-icon>
                    <span>修改信息</span>
                </el-button>
            </div>

            <div class="stat-card">
                <div class="stat-item">
                    <b>{{ totalDays }}</b>
                    <span>记录天数</span>
                </div>
                <div class="stat-item">
                    <b>{{ streak }}</b>
                    <span>连续记录</span>
                </div>
                <div class="stat-item">
                    <b>{{ monthCount }}</b>
                    <span>本月记录</span>
                </div>
            </div>
        </aside>

        <!-- ================= 右栏：贡献日历 ================= -->
        <main class="main">
            <section class="card">
                <div class="card-head">
                    <h3>近三个月的情绪记录</h3>
                    <span class="card-sub">{{ totalDays }} 天有记录</span>
                </div>

                <div class="calendar">
                    <!-- 月份标签：和格子用同一套列宽 -->
                    <div class="month-row">
                        <span class="month-blank"></span>
                        <div class="month-track">
                            <span v-for="m in monthLabels" :key="m.key" class="month-label"
                                :style="{ gridColumn: m.col }">{{ m.text }}</span>
                        </div>
                    </div>

                    <div class="calendar-body">
                        <div class="weekday-col">
                            <span></span>
                            <span>一</span>
                            <span></span>
                            <span>三</span>
                            <span></span>
                            <span>五</span>
                            <span></span>
                        </div>

                        <div class="cell-grid">
                            <div v-for="cell in cells" :key="cell.date" class="cell"
                                :class="{ future: cell.future, empty: !cell.record }"
                                :style="{ background: cell.color }" :title="cell.tip"></div>
                        </div>
                    </div>
                </div>

                <div class="legend">
                    <span class="legend-text">少</span>
                    <i v-for="c in legendColors" :key="c" :style="{ background: c }"></i>
                    <span class="legend-text">多</span>
                    <span class="legend-note">颜色越深，当天心情越好</span>
                </div>
            </section>

            <section class="card">
                <div class="card-head">
                    <h3>最近记录</h3>
                    <span class="card-sub">最近 {{ recent.length }} 天</span>
                </div>
                <div class="recent-list" v-if="recent.length">
                    <div class="recent-item" v-for="r in recent" :key="r.date">
                        <span class="recent-date">{{ r.date }}</span>
                        <span class="recent-dot" :style="{ background: levelColor(r.moodScore) }"></span>
                        <span class="recent-emotion">{{ r.dominantEmotion || '未选择' }}</span>
                        <span class="recent-score">{{ r.moodScore }} 分</span>
                    </div>
                </div>
                <p class="empty-tip" v-else>还没有记录，去<a href="/emotion-diary">写一篇</a>吧</p>
            </section>
        </main>

        <!-- ================= 修改信息弹框 ================= -->
        <el-dialog v-model="editVisible" title="修改个人信息" width="440px"
            :close-on-click-modal="false" append-to-body>
            <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="72px">
                <el-form-item label="用户名">
                    <el-input :model-value="user.username || ''" disabled />
                    <p class="form-tip">用户名是登录凭据，暂不支持修改</p>
                </el-form-item>

                <el-form-item label="昵称" prop="nickname">
                    <el-input v-model="editForm.nickname" maxlength="50" show-word-limit
                        placeholder="别人看到的名字" />
                </el-form-item>

                <el-form-item label="邮箱" prop="email">
                    <el-input v-model="editForm.email" placeholder="用于登录，不能和别人重复" />
                </el-form-item>

                <el-form-item label="手机号" prop="phone">
                    <el-input v-model="editForm.phone" maxlength="11" placeholder="选填" />
                </el-form-item>

                <el-form-item label="性别" prop="gender">
                    <el-radio-group v-model="editForm.gender">
                        <el-radio :value="1">男</el-radio>
                        <el-radio :value="2">女</el-radio>
                        <el-radio :value="0">保密</el-radio>
                    </el-radio-group>
                </el-form-item>

                <el-form-item label="生日" prop="birthday">
                    <el-date-picker v-model="editForm.birthday" type="date" value-format="YYYY-MM-DD"
                        placeholder="选填" style="width:100%" :disabled-date="isFutureDate" />
                </el-form-item>
            </el-form>

            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Message, Calendar, Loading, EditPen, Iphone, Present } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { getUserActivity, updateAvatar, uploadFile, updateProfile } from '@/api/frontend'
import { useUserStore } from '@/stores/user'

//用户信息放 store：顶栏和个人主页读同一个 ref，这边换完头像那边立刻跟着变
const userStore = useUserStore()
const user = computed(() => userStore.userInfo)

const activityMap = ref({})      // 'YYYY-MM-DD' -> { moodScore, dominantEmotion }
const uploading = ref(false)
const fileInput = ref(null)
const uploadCtrl = ref(null)     // AbortController，用来中途取消
const previewUrl = ref('')       // 选中文件后立刻本地预览，选错了当场能看出来

//贡献图横跨的周数。13 周 ≈ 三个月，跟后端 /user/activity 的 91 天对齐
const WEEKS = 13

const LEVELS = ['#EBEDF0', '#C6E48B', '#7BC96F', '#239A3B', '#196127']
const legendColors = LEVELS.slice(1)

const levelColor = (score) => {
    if (!score) return LEVELS[0]
    if (score <= 3) return LEVELS[1]
    if (score <= 6) return LEVELS[2]
    if (score <= 8) return LEVELS[3]
    return LEVELS[4]
}

const initial = computed(() => {
    const n = user.value.nickname || user.value.username || ''
    return n ? n.charAt(0).toUpperCase() : '?'
})

//后端返回的已经是完整 URL 了，这里只做兜底
const avatarUrl = computed(() => previewUrl.value || user.value.avatar || '')

const bio = computed(() => {
    const n = totalDays.value
    if (!n) return '还没有开始记录，今天就是第一天。'
    return `已经记录了 ${n} 天的情绪，继续保持。`
})

const joinedAt = computed(() => {
    const t = user.value.createdAt
    return t ? dayjs(t).format('YYYY年M月') : ''
})

const totalDays = computed(() => Object.keys(activityMap.value).length)

const streak = computed(() => {
    let d = dayjs()
    if (!activityMap.value[d.format('YYYY-MM-DD')]) d = d.subtract(1, 'day')
    let n = 0
    while (activityMap.value[d.format('YYYY-MM-DD')]) {
        n++
        d = d.subtract(1, 'day')
        if (n > 366) break
    }
    return n
})

const monthCount = computed(() => {
    const m = dayjs().format('YYYY-MM')
    return Object.keys(activityMap.value).filter(k => k.startsWith(m)).length
})

const recent = computed(() => {
    return Object.entries(activityMap.value)
        .sort((a, b) => (a[0] < b[0] ? 1 : -1))
        .slice(0, 6)
        .map(([date, v]) => ({ date, ...v }))
})

//13 周 × 7 天，从 12 周前的周日到今天所在周的周六
const cells = computed(() => {
    const today = dayjs()
    const start = today.subtract(WEEKS - 1, 'week').startOf('week')
    const arr = []
    for (let i = 0; i < WEEKS * 7; i++) {
        const d = start.add(i, 'day')
        const key = d.format('YYYY-MM-DD')
        const rec = activityMap.value[key]
        const future = d.isAfter(today, 'day')
        arr.push({
            date: key,
            future,
            record: rec,
            color: future ? 'transparent' : levelColor(rec && rec.moodScore),
            tip: rec
                ? `${key} · ${rec.dominantEmotion || '未选择'} · ${rec.moodScore} 分`
                : `${key} · 没有记录`
        })
    }
    return arr
})

//月份标签：每列的日期里有新的月份就标一次
const monthLabels = computed(() => {
    const today = dayjs()
    const start = today.subtract(WEEKS - 1, 'week').startOf('week')
    const labels = []
    let lastMonth = -1
    for (let w = 0; w < WEEKS; w++) {
        const d = start.add(w * 7, 'day')
        if (d.month() !== lastMonth) {
            lastMonth = d.month()
            labels.push({
                key: `${d.year()}-${d.month()}`,
                text: `${d.month() + 1}月`,
                col: w + 1
            })
        }
    }
    return labels
})

//先用本地缓存顶上（避免白屏），再用 /user/current 拉最新的
//只读 localStorage 的话，换完头像一刷新又变回旧图
const loadActivity = () => {
    getUserActivity().then(data => {
        const map = {}
        ;(data || []).forEach(item => {
            map[item.diaryDate] = {
                moodScore: item.moodScore,
                dominantEmotion: item.dominantEmotion
            }
        })
        activityMap.value = map
    }).catch(() => {
        activityMap.value = {}
    })
}

const pickFile = () => {
    if (uploading.value) return
    fileInput.value && fileInput.value.click()
}

const clearPreview = () => {
    if (previewUrl.value) {
        URL.revokeObjectURL(previewUrl.value)
        previewUrl.value = ''
    }
}

//中途取消：掐断请求，预览回退成原头像
const cancelUpload = () => {
    if (uploadCtrl.value) uploadCtrl.value.abort()
}

const isCanceled = (err) => err && (err.name === 'CanceledError' || err.code === 'ERR_CANCELED')

const onFileChange = async (e) => {
    const file = e.target.files && e.target.files[0]
    e.target.value = ''
    if (!file) return
    if (!file.type.startsWith('image/')) {
        ElMessage.warning('只能上传图片')
        return
    }
    if (file.size > 5 * 1024 * 1024) {
        ElMessage.warning('图片不能超过 5MB')
        return
    }

    //先本地预览，图还没传上去就能看出选没选错
    clearPreview()
    previewUrl.value = URL.createObjectURL(file)

    const ctrl = new AbortController()
    uploadCtrl.value = ctrl
    uploading.value = true
    try {
        const fd = new FormData()
        fd.append('file', file)
        fd.append('businessType', 'AVATAR')
        fd.append('businessId', String(user.value.id || ''))
        fd.append('businessField', 'avatar')

        const res = await uploadFile(fd, ctrl.signal)
        //库里存 objectKey（filePath），fileUrl 只用于显示
        const saved = await updateAvatar(res.filePath)

        //写进 store 而不是本地变量：顶栏订阅的是同一个 ref，会立刻跟着换图
        userStore.setUser({ avatar: saved?.avatar || res.fileUrl })
        clearPreview()
        ElMessage.success('头像已更新')
    } catch (err) {
        clearPreview()
        if (isCanceled(err)) {
            ElMessage.info('已取消上传')
        } else {
            ElMessage.error('头像更新失败')
        }
    } finally {
        uploading.value = false
        uploadCtrl.value = null
    }
}

//=================== 修改信息 ===================
const editVisible = ref(false)
const saving = ref(false)
const editFormRef = ref(null)
const editForm = ref({ nickname: '', email: '', phone: '', gender: 0, birthday: '' })

const editRules = {
    email: [
        { required: true, message: '邮箱不能为空', trigger: 'blur' },
        { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
    ],
    phone: [
        { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
    ]
}

//生日不能选未来，跟后端 @Past 对齐
const isFutureDate = (d) => d.getTime() > Date.now()

const openEdit = () => {
    //回显：直接拿当前用户信息填进去，用户只改想改的那几项
    editForm.value = {
        nickname: user.value.nickname || '',
        email: user.value.email || '',
        phone: user.value.phone || '',
        gender: user.value.gender ?? 0,
        birthday: user.value.birthday || ''
    }
    editVisible.value = true
    //弹框刚渲染出来时表单还是空的，等 DOM 更新完再清上一次的校验红字
    nextTick(() => editFormRef.value && editFormRef.value.clearValidate())
}

const submitEdit = async () => {
    if (!editFormRef.value) return
    const valid = await editFormRef.value.validate().catch(() => false)
    if (!valid) return

    saving.value = true
    try {
        const body = { ...editForm.value }
        //手机号空串要保留 —— 后端拿它当"清空"，删掉就变成"这次不改"了
        //生日空值删掉：LocalDate 解析 "" 会报错，null 又表达不了"清空"，干脆不动
        if (!body.birthday) delete body.birthday

        const me = await updateProfile(body)
        //同样走 store：改完昵称，顶栏的名字也立刻变
        userStore.setUser(me)
        editVisible.value = false
        ElMessage.success('资料已更新')
    } catch (err) {
        //错误提示拦截器已经统一弹过了，这里只负责把按钮解锁
    } finally {
        saving.value = false
    }
}

onMounted(() => {
    userStore.load()
    loadActivity()
})
</script>

<style lang="scss" scoped>
.profile-page {
    width: 100%;
    max-width: 1200px;
    margin: 0 auto;
    /* 顶栏只有 65px 高，28px 会显得挤着顶栏，往上留出呼吸空间 */
    padding: 48px 20px 60px;
    box-sizing: border-box;
    display: flex;
    align-items: flex-start;
    gap: 24px;
}

/* ---------------- 左栏 ---------------- */
.sidebar {
    width: 280px;
    flex-shrink: 0;
    position: sticky;
    top: 20px;
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.profile-card {
    background: #fff;
    border: 1px solid #E5E7EB;
    border-radius: 14px;
    padding: 28px 20px 22px;
    text-align: center;
}

.avatar-wrap {
    position: relative;
    width: 92px;
    height: 92px;
    margin: 0 auto 16px;
    border-radius: 50%;
    overflow: hidden;

    .avatar {
        font-size: 34px;
        background: #7ED321;
        color: #fff;
    }

    .avatar-mask {
        position: absolute;
        inset: 0;
        background: rgba(0, 0, 0, 0.45);
        color: #fff;
        font-size: 13px;
        display: flex;
        align-items: center;
        justify-content: center;
        opacity: 0;
        transition: opacity 0.2s;
        cursor: pointer;
    }

    &:hover .avatar-mask {
        opacity: 1;
    }

    /* 上传中始终可见，不靠 hover —— 不然用户一移开鼠标就不知道在传了 */
    .avatar-mask.progress {
        opacity: 1;
        cursor: default;
        flex-direction: column;
        gap: 6px;

        .spin {
            font-size: 18px;
        }

        .cancel-btn {
            border: 1px solid rgba(255, 255, 255, 0.65);
            background: transparent;
            color: #fff;
            font-size: 12px;
            line-height: 1;
            padding: 4px 12px;
            border-radius: 10px;
            cursor: pointer;
            transition: background 0.15s;

            &:hover {
                background: rgba(255, 255, 255, 0.2);
            }
        }
    }
}

.hidden-input {
    display: none;
}

.nickname {
    margin: 0 0 4px;
    font-size: 18px;
    font-weight: 600;
    color: #1F2937;
}

.username {
    margin: 0 0 14px;
    font-size: 14px;
    color: #9CA3AF;
}

.bio {
    margin: 0 0 18px;
    font-size: 13px;
    line-height: 1.7;
    color: #6B7280;
    padding: 0 4px;
}

.meta-list {
    list-style: none;
    margin: 0;
    padding: 16px 0 0;
    border-top: 1px solid #F3F4F6;
    text-align: left;

    li {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 13px;
        color: #6B7280;
        padding: 5px 0;

        .el-icon {
            color: #9CA3AF;
            flex-shrink: 0;
        }

        span {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
    }
}

.edit-btn {
    width: 100%;
    margin-top: 14px;
    height: 34px;
    border-radius: 8px;
    border-color: #E5E7EB;
    color: #4B5563;
    font-size: 13px;

    &:hover {
        border-color: #7ED321;
        color: #5BA80F;
        background: #F7FDF0;
    }
}

/* el-form-item 的内容区是 flex-wrap:wrap，提示文字给 width:100% 才会换到下一行 */
.form-tip {
    width: 100%;
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 1.5;
    color: #9CA3AF;
}

.stat-card {
    background: #fff;
    border: 1px solid #E5E7EB;
    border-radius: 14px;
    padding: 18px 12px;
    display: flex;
    .stat-item {
        flex: 1;
        text-align: center;

        & + .stat-item {
            border-left: 1px solid #F3F4F6;
        }

        b {
            display: block;
            font-size: 20px;
            font-weight: 600;
            color: #1F2937;
            line-height: 1.4;
        }

        span {
            font-size: 12px;
            color: #9CA3AF;
        }
    }
}

/* ---------------- 右栏 ---------------- */
.main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.card {
    background: #fff;
    border: 1px solid #E5E7EB;
    border-radius: 14px;
    padding: 22px 24px;
}

.card-head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    margin-bottom: 20px;

    h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
        color: #1F2937;
    }

    .card-sub {
        font-size: 13px;
        color: #9CA3AF;
    }
}

/* 日历
   13 周 × 7 天，格子放大到 32px —— 只显示三个月，再按 GitHub 那种 11px 密排就太小了。
   步进 = 32(格子) + 6(gap) = 38px，月份标签和星期列都按这个对齐。 */
.calendar {
    width: 100%;
    overflow-x: auto;
    padding-bottom: 4px;
}

.month-row {
    display: flex;
    justify-content: center;
    margin-bottom: 8px;

    .month-blank {
        width: 32px;
        flex-shrink: 0;
    }

    .month-track {
        display: grid;
        grid-auto-flow: column;
        grid-auto-columns: 32px;
        column-gap: 6px;
        flex-shrink: 0;
    }

    .month-label {
        font-size: 12px;
        color: #9CA3AF;
        white-space: nowrap;
    }
}

.calendar-body {
    display: flex;
    justify-content: center;
    align-items: flex-start;
}

.weekday-col {
    width: 32px;
    flex-shrink: 0;
    display: grid;
    grid-template-rows: repeat(7, 32px);
    row-gap: 6px;

    span {
        font-size: 11px;
        line-height: 32px;
        color: #9CA3AF;
        text-align: right;
        padding-right: 8px;
    }
}

.cell-grid {
    display: grid;
    grid-template-rows: repeat(7, 32px);
    grid-auto-flow: column;
    grid-auto-columns: 32px;
    gap: 6px;
    flex-shrink: 0;

    .cell {
        width: 32px;
        height: 32px;
        border-radius: 5px;
        transition: transform 0.12s;

        &:hover {
            transform: scale(1.12);
            outline: 1px solid #9CA3AF;
        }

        &.future {
            background: transparent !important;
            outline: 1px dashed #F3F4F6;
        }
    }
}

.legend {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    margin-top: 18px;
    font-size: 12px;
    color: #9CA3AF;

    i {
        display: inline-block;
        width: 14px;
        height: 14px;
        border-radius: 3px;
    }

    .legend-note {
        margin-left: 14px;
        color: #B4B2A9;
    }
}

/* 最近记录 */
.recent-list {
    display: flex;
    flex-direction: column;
}

.recent-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 0;
    font-size: 13px;
    color: #4B5563;

    & + .recent-item {
        border-top: 1px solid #F9FAFB;
    }

    .recent-date {
        width: 96px;
        color: #6B7280;
        font-variant-numeric: tabular-nums;
    }

    .recent-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        flex-shrink: 0;
    }

    .recent-emotion {
        flex: 1;
    }

    .recent-score {
        color: #9CA3AF;
    }
}

.empty-tip {
    margin: 0;
    font-size: 13px;
    color: #9CA3AF;

    a {
        color: #7ED321;
    }
}

/* 窄屏：左栏变上栏 */
@media (max-width: 900px) {
    .profile-page {
        flex-direction: column;
    }

    .sidebar {
        width: 100%;
        position: static;
    }
}
</style>
