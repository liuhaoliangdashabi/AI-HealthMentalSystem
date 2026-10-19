<template>
    <div class="emotionDiary-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width:50px;height:50px;"/>
                <h1>情绪日志</h1>
            </div>
        </div>
        <!-- 写日记 / 我的日记 切换 -->
        <div class="tab-bar">
            <div class="tab-item" :class="{active:activeTab==='write'}"
                 @click="activeTab='write'">
                {{ editingId ? '编辑日记' : '写日记' }}
            </div>
            <div class="tab-item" :class="{active:activeTab==='list'}"
                 @click="switchToList">我的日记</div>
        </div>

        <div class="main-area">
        <!-- 左侧状态框：显示当前是新增还是修改哪一天 -->
        <div class="status-panel" :class="{placeholder: activeTab!=='write'}">
            <div class="status-tag" :class="{editing:editingId}">
                {{ editingId ? '编辑中' : '新增日记' }}
            </div>
            <div class="status-date">{{ diaryForm.diaryDate }}</div>
            <div class="status-hint">{{ editingId ? '改完点保存修改' : '写完后点提交记录' }}</div>
            <el-button v-if="editingId" type="success" plain size="small"
                       class="status-new-btn" @click="resetForm">
                + 新建日记
            </el-button>
        </div>

        <div class="content" v-show="activeTab==='write'">
            <!-- 情绪评分 -->
            <div class="diary-card">
                <div class="title">今日情绪评分</div>
                <div class="section">
                    <p>您今天的整体情绪状态如何?(1~10分)</p>
                    <div class="rate">
                        <el-rate
                            v-model="diaryForm.moodScore"
                            :texts="emotionStatus"
                            show-texts
                            :max="10"
                            size="large"
                        />
                    </div>
                </div>
            </div>
            <!-- 主要情绪 -->
            <div class="diary-card">
                <div class="title">主要情绪</div>
                <div class="emotion-grid">
                    <div v-for="emotion in emotionOptions" :key="emotion"
                        class="emotion-card" @click="selectEmotion(emotion.name)"
                        :class="{'selected':emotion.name===diaryForm.dominantEmotion}">
                        <el-image :src="emotion.url" style="width:40px;height:40px;"/>
                        <div class="emotion-name">{{ emotion.name }}</div>
                    </div>
                </div>
            </div>
            <!-- 详细记录 -->
            <div class="diary-card">
                <div class="title">详细记录</div>
                <div class="detail-form">
                    <div class="form-group">
                        <div class="form-label">情绪触发因素</div>
                        <el-input type="textarea" v-model="diaryForm.emotionTriggers"
                            placeholder="今天什么事情影响了您的情绪？" :rows="3"
                            maxLength="300" show-word-limit/>
                    </div>
                    <div class="form-group">
                        <div class="form-label">今日感想</div>
                        <el-input type="textarea" v-model="diaryForm.diaryContent"
                            placeholder="写下今天的想法、感受或发生的有趣事情..." :rows="3"
                            maxLength="300" show-word-limit/>
                    </div>
                    <!-- 生活指标 -->
                    <div class="life-indicators">
                        <div class="indicator-group">
                            <div class="form-label">睡眠质量</div>
                            <el-select v-model="diaryForm.sleepQuality"
                                placeholder="请选择">
                                <el-option label="很差" :value="1"></el-option>
                                <el-option label="较差" :value="2"></el-option>
                                <el-option label="一般" :value="3"></el-option>
                                <el-option label="良好" :value="4"></el-option>
                                <el-option label="很好" :value="5"></el-option>
                            </el-select>
                        </div>
                        <div class="indicator-group">
                            <div class="form-label">压力水平</div>
                            <el-select v-model="diaryForm.stressLevel"
                                placeholder="请选择">
                                <el-option label="很低" :value="1"></el-option>
                                <el-option label="较低" :value="2"></el-option>
                                <el-option label="一般" :value="3"></el-option>
                                <el-option label="较高" :value="4"></el-option>
                                <el-option label="很高" :value="5"></el-option>
                            </el-select>
                        </div>
                    </div>

                    <div class="action-buttons">
                        <el-button @click="resetForm">重置</el-button>
                        <el-button type="primary" @click="submit">
                            {{ editingId ? '保存修改' : '提交记录' }}
                        </el-button>
                    </div>
                </div>
            </div>
        </div>

        <div class="content" v-show="activeTab==='list'">
            <div class="diary-card" v-if="diaryList.length===0">
                <el-empty description="还没有写过日记"/>
            </div>

            <div class="diary-card diary-item" v-for="d in diaryList" :key="d.id">
                <div class="item-head">
                    <span class="item-date">{{ d.diaryDate }}</span>
                    <span class="item-meta">
                        <img v-if="emotionIcon(d.dominantEmotion)" :src="emotionIcon(d.dominantEmotion)" class="emotion-mini"/>
                        {{ d.dominantEmotion || '未选择' }} · {{ d.moodScore }} 分
                    </span>
                </div>
                <p class="item-content">{{ d.diaryContent || '（没有写感想）' }}</p>
                <div class="item-foot">
                    <span class="item-updated">
                        {{ isEdited(d) ? '最后修改' : '创建于' }}
                        {{ formatTime(d.updatedAt || d.createdAt) }}
                    </span>
                    <div class="item-actions">
                        <el-button size="small" @click="handleEdit(d)">编辑</el-button>
                        <el-button size="small" type="danger" plain @click="handleDelete(d.id)">删除</el-button>
                    </div>
                </div>
            </div>

            <div class="pager" v-if="total>size">
                <el-pagination
                    layout="prev, pager, next"
                    :total="total"
                    :page-size="size"
                    :current-page="current"
                    @current-change="handlePageChange"
                />
            </div>
        </div>
        </div>
    </div>
</template>

<script setup>
import {dayjs, ElMessage, ElMessageBox} from 'element-plus'
import {ref,reactive,onMounted} from 'vue'
import { addEmotionDiary, getMyDiaries, deleteMyDiary, updateEmotionDiary } from '@/api/frontend'

const emotionStatus = ['绝望崩溃', '消沉抑郁', '焦虑烦躁', '低落不悦', '平静淡然', '轻松惬意', '愉悦舒心', '欢欣满足', '兴奋欣喜', '极致幸福']
const diaryForm=reactive({
    diaryDate:dayjs().format('YYYY-MM-DD'),
    moodScore:null,
    dominantEmotion:'',
    emotionTriggers:'',
    diaryContent:'',
    sleepQuality:null,
    stressLevel:null
})
const emotionOptions=[
    {name:'开心',url:new URL('@/assets/开心.png',import.meta.url).href},
    {name:'平静',url:new URL('@/assets/平静.png',import.meta.url).href},
    {name:'焦虑',url:new URL('@/assets/焦虑.png',import.meta.url).href},
    {name:'悲伤',url:new URL('@/assets/悲伤.png',import.meta.url).href},
    {name:'兴奋',url:new URL('@/assets/兴奋.png',import.meta.url).href},
    {name:'疲惫',url:new URL('@/assets/疲惫.png',import.meta.url).href},
    {name:'惊讶',url:new URL('@/assets/惊讶.png',import.meta.url).href},
    {name:'困惑',url:new URL('@/assets/困惑.png',import.meta.url).href},
]
const iconUrl=new URL('@/assets/like.png',import.meta.url).href

//当前视图：write 写日记 / list 我的日记
const activeTab=ref('write')
//列表数据
const diaryList=ref([])
const total=ref(0)
const current=ref(1)
const size=ref(5)
//编辑中的日记id，null 表示新增
const editingId=ref(null)

const selectEmotion=(name)=>{
    diaryForm.dominantEmotion=name
}

const resetForm=()=>{
    editingId.value=null
    Object.assign(diaryForm,{
        diaryDate:dayjs().format('YYYY-MM-DD'),
        moodScore:null,
        dominantEmotion:'',
        emotionTriggers:'',
        diaryContent:'',
        sleepQuality:null,
        stressLevel:null
    })
}

const loadDiaries=()=>{
    getMyDiaries({current:current.value,size:size.value}).then(data=>{
        diaryList.value=data.records||[]
        total.value=data.total||0
    })
}

//列表里显示时间用
const formatTime=(t)=>{
    if(!t) return ''
    return dayjs(t).format('YYYY-MM-DD HH:mm')
}

//根据情绪名找对应的图标，找不到返回空
const emotionIcon=(name)=>{
    const hit=emotionOptions.find(e=>e.name===name)
    return hit?hit.url:''
}

//updatedAt 和 createdAt 不同，说明编辑过
const isEdited=(d)=>{
    return !!d.updatedAt && !!d.createdAt && d.updatedAt!==d.createdAt
}

const switchToList=()=>{
    activeTab.value='list'
    loadDiaries()
}

const handlePageChange=(page)=>{
    current.value=page
    loadDiaries()
}

//点编辑：把这条日记回填到表单，切到写日记视图
const handleEdit=(d)=>{
    editingId.value=d.id
    Object.assign(diaryForm,{
        diaryDate:d.diaryDate,
        moodScore:d.moodScore,
        dominantEmotion:d.dominantEmotion||'',
        emotionTriggers:d.emotionTriggers||'',
        diaryContent:d.diaryContent||'',
        sleepQuality:d.sleepQuality,
        stressLevel:d.stressLevel
    })
    activeTab.value='write'
}

const handleDelete=(id)=>{
    ElMessageBox.confirm('确定删除这篇日记吗？','提示',{type:'warning'})
        .then(()=>deleteMyDiary(id))
        .then(()=>{
            ElMessage.success('已删除')
            //删的是本页最后一条且不在第一页，往前翻一页
            if(diaryList.value.length===1 && current.value>1){
                current.value--
            }
            loadDiaries()
        })
        .catch(()=>{})
}

const submit=()=>{
    if(!diaryForm.moodScore){
        ElMessage.warning('请选择情绪评分')
        return
    }
    if(editingId.value){
        updateEmotionDiary(editingId.value,diaryForm).then(()=>{
            ElMessage.success('修改成功')
            resetForm()
            switchToList()
        })
        return
    }
    addEmotionDiary(diaryForm).then(()=>{
        ElMessage.success('提交成功')
        resetForm()
    })
}

onMounted(()=>{
    resetForm()
})
</script>

<style lang="scss" scoped>
.emotionDiary-container {
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    .header-section {
        background: linear-gradient(135deg, #7ED321 0%, #F5A623 100%);
        color: white;
        padding: 22px;
        .header-content {
            display: flex;
            align-items: center;
            gap: 12px;
            h1 {
                margin: 0;
            }
        }
    }
    .tab-bar {
        width: 100%;
        max-width: 1200px;
        box-sizing: border-box;
        margin: 0 auto;
        //200 = 20(内边距) + 160(状态框) + 20(间距)，让 tab 和右侧表单左边缘对齐
        padding: 20px 20px 0 200px;
        display: flex;
        gap: 10px;
        flex-wrap: wrap;
        .tab-item {
            padding: 8px 24px;
            border-radius: 8px;
            font-size: 15px;
            color: #6B7280;
            background: white;
            border: 1px solid #E5E7EB;
            cursor: pointer;
            transition: all 0.2s;
            &.active {
                color: white;
                background: #7ED321;
                border-color: #7ED321;
            }
        }
    }
    //写日记区：左侧状态框 + 右侧表单并排
    //原来是 fixed + 写死的 top:120px，靠猜"横幅下面大概在哪"，
    //页面上方有导航栏时必然压住横幅标题 —— 参与文档流就没这个问题
    .main-area {
        display: flex;
        align-items: flex-start;
        gap: 20px;
        max-width: 1200px;
        margin: 0 auto;
        padding: 20px;
        box-sizing: border-box;
    }
    //左侧状态框
    .status-panel {
        width: 160px;
        flex-shrink: 0;
        position: sticky;
        top: 20px;
        background: white;
        border-radius: 10px;
        padding: 16px;
        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
        .status-tag {
            display: inline-block;
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 13px;
            color: #6B7280;
            background: #F3F4F6;
            &.editing {
                color: white;
                background: #F5A623;
            }
        }
        .status-date {
            margin-top: 12px;
            font-size: 16px;
            font-weight: 600;
            color: #374151;
        }
        .status-hint {
            margin-top: 6px;
            font-size: 12px;
            color: #9CA3AF;
            line-height: 1.5;
        }
        .status-new-btn {
            margin-top: 14px;
            width: 100%;
        }
        //切到「我的日记」时保留占位（不显示但占宽），否则列表会整体左跳 180px
        &.placeholder {
            visibility: hidden;
        }
    }
    @media (max-width: 1400px) {
        .status-panel {
            display: none;
        }
        .tab-bar {
            padding-left: 20px;
        }
    }
    .content {
        flex: 1;
        min-width: 0;
        max-width: 980px;
        box-sizing: border-box;
        padding: 20px 0;
        .diary-card {
            margin-bottom: 20px;
            background: white;
            border-radius: 10px;
            padding: 20px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
            .title {
                margin-bottom: 20px;
                font-size: 25px;
                font-weight: 600;
                color: #374151;
            }
            .section {
                margin-bottom: 20px;
                p {
                    font-size: 15px;
                    color: #6B7280;
                    margin-bottom: 15px;
                }
            }
            .emotion-grid {
                display: flex;
                flex-wrap: wrap;
                gap: 10px;
                .emotion-card {
                    padding: 15px;
                    border: 2px solid #E5E7EB;
                    border-radius: 15px;
                    text-align: center;
                    cursor: pointer;
                    background: #F9FAFB;
                    .emotion-name {
                        margin-top: 10px;
                        padding: 0 75px;
                        color: #374151;
                    }
                    &.selected {
                        border-color: #7ED321;
                        background: #F0FDF4;
                        transform: translateY(-3px);
                    }
                }
            }
            .detail-form {
                .form-label {
                    margin: 10px 0;
                    color: #374151;
                }
                .life-indicators {
                    display: flex;
                    gap: 20px;
                    flex-wrap: wrap;
                    .indicator-group {
                        flex: 1;
                        min-width: 180px;
                    }
                }
                .action-buttons {
                    margin-top: 40px
                }
            }
        }
        //日记列表项
        .diary-item {
            .item-head {
                display: flex;
                justify-content: space-between;
                align-items: center;
                gap: 8px;
                flex-wrap: wrap;
                margin-bottom: 10px;
                .item-date {
                    font-size: 17px;
                    font-weight: 600;
                    color: #374151;
                }
                .item-meta {
                    font-size: 14px;
                    color: #6B7280;
                    display: inline-flex;
                    align-items: center;
                    gap: 4px;
                    .emotion-mini {
                        width: 18px;
                        height: 18px;
                        object-fit: contain;
                    }
                }
            }
            .item-content {
                font-size: 14px;
                color: #6B7280;
                line-height: 1.7;
                margin: 0 0 14px;
            }
            .item-foot {
                display: flex;
                justify-content: space-between;
                align-items: center;
                gap: 8px;
                flex-wrap: wrap;
                .item-updated {
                    font-size: 12px;
                    color: #9CA3AF;
                }
            }
            .item-actions {
                display: flex;
                gap: 8px;
            }
        }
        .pager {
            display: flex;
            justify-content: center;
            margin-top: 10px;
        }
    }
}
</style>
