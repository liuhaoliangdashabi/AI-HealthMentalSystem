<template>
    <div>
        <PageHead title="情绪日志"/>
        <TableSearch :formItem="formItem" @search="handleSearch"/>
        <el-table :data="tableData" style="width:100%">
            <el-table-column prop="userId" label="用户ID" width="80"/>
            <el-table-column prop="username" label="用户" width="120" show-overflow-tooltip/>
            <el-table-column prop="diaryDate" label="记录日期" width="110"/>
            <el-table-column label="情绪评分" width="180">
                <template #default="scope">
                    <el-rate :model-value="scope.row.moodScore" :max="10" disabled/>
                </template>
            </el-table-column>
            <el-table-column label="生活指标" width="110">
                <template #default="scope">
                    <div>
                        <p>睡眠：{{ scope.row.sleepQuality }}/5</p>
                        <p>压力：{{ scope.row.stressLevel }}/5</p>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="emotionTriggers" label="情绪触发因素" width="150" show-overflow-tooltip/>
            <el-table-column prop="diaryContent" label="日记内容" width="240" show-overflow-tooltip/>
            <el-table-column label="操作" width="140" fixed="right">
                <template #default="scope">
                    <el-button @click="viewSessionDetail(scope.row)" class="geren-btn-minpadding" text type="primary">详情</el-button>
                    <el-button @click="handleDelete(scope.row)" class="geren-btn-minpadding" text type="danger">删除</el-button>
                </template>
            </el-table-column>
        </el-table>
        <el-pagination
            style="margin-top:15px"
            :page-size="pagination.size"
            layout="prev,pager,next"
            :total="pagination.total"
            @change="handleChange"/>
        <el-dialog
            v-model="detailDialogVisible"
            title="情绪日志详情"
            width="800px"
            :close-on-click-modal="false">
            <div class="detail-content" v-if="currentDetail">
                <div class="detail-section">
                    <h4>用户信息</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="用户名">{{ currentDetail.username }}</el-descriptions-item>
                        <el-descriptions-item label="昵称">{{ currentDetail.nickname }}</el-descriptions-item>
                        <el-descriptions-item label="用户ID">{{ currentDetail.userId }}</el-descriptions-item>
                        <el-descriptions-item label="记录日期">{{ currentDetail.diaryDate }}</el-descriptions-item>
                    </el-descriptions> 
                </div>
                <div class="detail-section">
                    <h4>情绪状态</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="情绪评分">
                            <el-rate :model-value="currentDetail.moodScore" :max="10" disabled/>
                        </el-descriptions-item>
                        <el-descriptions-item label="主要情绪">
                            <el-tag :type="getEmotionTagType(currentDetail.dominantEmotion)">{{ currentDetail.dominantEmotion||'-' }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="睡眠质量">{{ currentDetail.sleepQuality||'-' }}/5</el-descriptions-item>
                        <el-descriptions-item label="压力水平">{{ currentDetail.stressLevel||'-' }}/5</el-descriptions-item>
                    </el-descriptions> 
                </div>
                <div class="detail-section">
                    <h4>日记内容</h4>
                    <el-descriptions :column="1" border>
                        <el-descriptions-item label="情绪触发因素">{{ currentDetail.emotionTriggers||'无' }}</el-descriptions-item>
                        <el-descriptions-item label="日记内容">{{ currentDetail.diaryContent||'无' }}</el-descriptions-item>
                    </el-descriptions> 
                </div>
                <div class="detail-section">
                    <div class="section-head">
                        <h4>AI情绪分析结果</h4>
                        <!-- 老日记没有分析结果（功能上线前写的），给个补算入口 -->
                        <el-button v-if="currentDetail && !currentDetail.aiEmotionAnalysis"
                            size="small" type="primary" plain
                            :loading="analyzing" @click="triggerAnalyze">
                            {{ analyzing ? '分析中...' : '生成分析' }}
                        </el-button>
                    </div>
                    <div class="ai-analysis-result">
                        <el-descriptions :column="2" border>
                            <el-descriptions-item label="主要情绪">
                                <el-tag :type="getAiEmotionTagType(aiData.primaryEmotion)">{{ aiData.primaryEmotion||'-' }}</el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="情绪强度">
                                <el-progress :percentage="aiData.emotionScore" 
                                    :color="getEmotionScoreColor(aiData.emotionScore)"
                                    :stroke-width="8"/>
                            </el-descriptions-item>
                            <el-descriptions-item label="风险等级">
                                <el-tag :type="getRiskLevelTagType(aiData.riskLevel)">{{ getRiskLevelText(aiData.riskLevel) }}</el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="情绪性质">
                                <el-tag :type="aiData.isNegative?'danger':'success'">{{ aiData.isNegative?'负面情绪':'正面情绪' }}</el-tag>
                            </el-descriptions-item>
                        </el-descriptions>
                        <div class="ai-suggestion-section">
                            <h5>专业建议</h5>
                            <div class="suggestion-content">{{ aiData.suggestion||'无' }}</div>
                        </div>
                        <div class="ai-risk-section">
                            <h5>风险描述</h5>
                            <div class="risk-content">{{ aiData.riskDescription||'无' }}</div>
                        </div>
                        <div class="ai-suggestion-section">
                            <h5>改善建议</h5>
                            <ul class="improvement-list">
                                <li v-for="item in aiData.improvementSuggestions" :key="item">{{ item }}</li>
                            </ul>
                        </div>
                    </div>
                    
                </div>
                <div class="detail-section">
                    <h4>时间信息</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="创建时间">{{ currentDetail.createdAt }}</el-descriptions-item>
                        <el-descriptions-item label="更新时间">{{ currentDetail.updatedAt }}</el-descriptions-item>
                    </el-descriptions>
                </div>
            </div>
            <template #footer>
                <el-button @click="detailDialogVisible=false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import {ref,reactive,onMounted,onUnmounted} from 'vue'
import {getEmotionalPage,deleteEmotional,analyzeDiary} from '@/api/admin'
import { ElMessage,ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue'


const getEmotionTagType = (emotion) => {
  const emotionTypes = {
    '快乐': 'success',
    '平静': 'info',
    '兴奋': 'warning',
    '愤怒': 'danger',
    '悲伤': 'info',
    '焦虑': 'warning'
  }
  return emotionTypes[emotion] || 'info'
}

const getAiEmotionTagType = (emotion) => {
  const emotionTagMap = {
    '快乐': 'success',
    '平静': 'success',
    '兴奋': 'warning',
    '满足': 'success',
    '愤怒': 'danger',
    '悲伤': 'info',
    '焦虑': 'warning',
    '恐惧': 'danger',
    '沮丧': 'info',
    '压力': 'warning'
  }
  return emotionTagMap[emotion] || 'info'
}

const getEmotionScoreColor = (score) => {
  if (score >= 80) return '#f56c6c'
  if (score >= 60) return '#e6a23c'
  if (score >= 40) return '#909399'
  return '#67c23a'
}

const getRiskLevelTagType = (riskLevel) => {
  const riskTagMap = {
    0: 'success',
    1: 'info',
    2: 'warning',
    3: 'danger'
  }
  return riskTagMap[riskLevel] || 'info'
}

const getRiskLevelText = (riskLevel) => {
  const riskTextMap = {
    0: '正常',
    1: '关注',
    2: '预警',
    3: '危机'
  }
  return riskTextMap[riskLevel] || '未知风险等级'
}


const formItem=[
    {comp:'input',type:'number',prop:'userId',label:'用户ID',placeholder:'请输入用户ID'},
    {comp:'select',prop:'moodScreRange',label:'情绪评分',placeholder:'请选择评分范围',options:[
        //1-3 / 4-7 / 8-10：3+4+3 正好覆盖 1~10，不重叠也不遗漏
        //（原来写的是 1-3 / 4-6 / 7-9，最大只到 9，打 10 分的日记任何一档都筛不出来）
        {label:'低分(1-3分)',value:'1-3'},
        {label:'中分(4-7分)',value:'4-7'},
        {label:'高分(8-10分)',value:'8-10'}
    ]}
]
const tableData=ref([])
const pagination=reactive({
    current:1,
    size:5,
    total:0
})
const detailDialogVisible=ref(false)
const currentDetail=ref(null)
const aiData=ref(null)

const handleSearch=async(formData={})=>{
    //前端传的是 "1-3" 这种字符串区间，后端要的是 minMoodScore / maxMoodScore，在这里拆开
    const {moodScreRange,...rest}=formData
    const params={
        ...pagination,
        ...rest
    }
    if(moodScreRange){
        const [min,max]=moodScreRange.split('-')
        params.minMoodScore=Number(min)
        params.maxMoodScore=Number(max)
    }
    const {records,total}= await getEmotionalPage(params)
    tableData.value=records
    pagination.total=total
}
const handleDelete=(row)=>{
    ElMessageBox.confirm(
        `确认删除${row.diaryDate}吗？`,
        '确认',{
            confirmButtonText:'确定',
            cancelButtonText:'取消',
            type:'danger'
        }
    ).then(()=>{
        deleteEmotional(row.id).then(res=>{
            ElMessage.success('删除成功')
            handleSearch()
        })
    })
}
onMounted(()=>{
    handleSearch()
})
const handleChange=(page)=>{
    pagination.current=page
    handleSearch()
}
const viewSessionDetail=(row)=>{
    currentDetail.value=row
    if(row.aiEmotionAnalysis){
        aiData.value=JSON.parse(row.aiEmotionAnalysis)
    }else{
        aiData.value={}
        //★ 打开就自动补算，不用用户点按钮。
        //  后端有 60 秒防重锁 + "已有结果就跳过"，多开几次也只会真跑一次。
        triggerAnalyze(true)
    }
    detailDialogVisible.value=true
}

//补算 AI 分析：投递到 Stream 后轮询，算完自动把结果填进弹窗
//silent=true 时不弹"已提交"，因为用户没主动点（是打开弹窗自动触发的）
const analyzing=ref(false)
let analyzeTimer=null   //放在模块级，组件卸载时才清得掉
const triggerAnalyze=async(silent=false)=>{
    if(!currentDetail.value || analyzing.value) return
    const id=currentDetail.value.id
    analyzing.value=true
    try{
        await analyzeDiary(id)
        if(!silent) ElMessage.success('已提交，正在分析...')
        //消费者 800ms 一轮 + 模型 5~10 秒，20 次 × 1.2 秒 = 24 秒，够用了
        let tries=0
        //先掐掉上一轮，否则连点两次会留下一个没人清的定时器
        if(analyzeTimer){ clearInterval(analyzeTimer); analyzeTimer=null }
        analyzeTimer=setInterval(async()=>{
            tries++
            await handleSearch()
            const row=tableData.value.find(r=>r.id===id)
            if(row && row.aiEmotionAnalysis){
                clearInterval(analyzeTimer)
                analyzeTimer=null
                //用户可能已经切到别的日记了，别把结果写到新弹窗里
                if(currentDetail.value && currentDetail.value.id===id){
                    currentDetail.value=row
                    try{
                        aiData.value=JSON.parse(row.aiEmotionAnalysis)
                    }catch(err){
                        //后端存的不是合法 JSON（比如空串）时，别让整个弹窗崩掉
                        console.warn('AI 分析结果解析失败',err)
                    }
                }
                analyzing.value=false
                if(!silent) ElMessage.success('分析完成')
            }else if(tries>=20){
                clearInterval(analyzeTimer)
                analyzeTimer=null
                analyzing.value=false
                ElMessage.warning('分析还在排队，稍后重新打开详情看看')
            }
        },1200)
    }catch(e){
        analyzing.value=false
        ElMessage.error('提交失败')
    }
}

//离开页面时停掉轮询：不然它会继续每 1.2 秒打一次接口，最多白跑 20 次
onUnmounted(()=>{
    if(analyzeTimer){ clearInterval(analyzeTimer); analyzeTimer=null }
})

</script>

<style lang="scss" scoped>
.geren-btn-minpadding{
    padding-left:6px;
    padding-right:6px;
}

.detail-content {
  .detail-section {
    margin-bottom: 24px;

    //标题 + 右侧按钮（「生成分析」）
    .section-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 16px;

      h4 {
        margin: 0;   //父级 h4 自带 margin-bottom，这里要清掉，否则标题和按钮不在一条线上
      }
    }
    
    h4 {
      margin: 0 0 16px 0;
      color: #303133;
      font-size: 16px;
      
      i {
        margin-right: 8px;
        color: #409eff;
      }
    }
  }
}

// AI分析相关样式
.ai-analysis-status {
  .ai-status-tag {
    margin-bottom: 4px;
    
    i {
      margin-right: 4px;
    }
  }
  
  .ai-analysis-preview {
    font-size: 11px;
    color: #909399;
    margin-top: 2px;
  }
}

.ai-analysis-result {
  .ai-keywords-section,
  .ai-suggestion-section,
  .ai-risk-section,
  .ai-improvements-section {
    margin-top: 16px;
    padding: 12px;
    background-color: #f8f9fa;
    border-radius: 4px;
    
    h5 {
      margin: 0 0 8px 0;
      color: #606266;
      font-size: 14px;
      font-weight: 600;
      
      i {
        margin-right: 6px;
        color: #909399;
      }
    }
  }
  
  .keywords-container {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    
    .keyword-tag {
      background-color: #e1f3d8;
      color: #67c23a;
      border-color: #b3d8a4;
    }
  }
  
  .suggestion-content,
  .risk-content {
    line-height: 1.6;
    color: #606266;
    background-color: white;
    padding: 8px;
    border-radius: 4px;
    border: 1px solid #ebeef5;
  }
  
  .improvement-list {
    margin: 0;
    padding-left: 20px;
    
    li {
      margin-bottom: 4px;
      color: #606266;
      line-height: 1.5;
    }
  }
  
  .ai-analysis-meta {
    margin-top: 16px;
    padding-top: 12px;
    border-top: 1px solid #ebeef5;
    
    .analysis-time {
      margin: 0;
      font-size: 12px;
      color: #909399;
      
      i {
        margin-right: 4px;
      }
    }
  }
  
  .el-progress {
    .el-progress__text {
      font-size: 12px !important;
    }
  }
}
</style>