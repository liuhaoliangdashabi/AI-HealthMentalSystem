<template>
    <el-dialog
        :title="isEdit?'编辑文章':'新增文章'"
        v-model="dialogVisible"
        width="50%"
        @close="handleClose"
    >
        <el-form :model="formData" :rules="rules" ref="formRef" label-width="120px">
            <el-form-item label="文章标题" prop="title">
                <el-input v-model="formData.title" placeholder="请输入文章标题"
                    maxlength="200" show-word-limit clearable/>
            </el-form-item>
            <el-form-item label="所属分类" prop="categoryId">
                <el-select v-model="formData.categoryId" placeholder="请选择分类">
                    <el-option v-for="item in props.categories" :key="item.value"
                        :label="item.label" :value="item.value"/>
                </el-select>
            </el-form-item>
            <el-form-item label="文章摘要" prop="summary">
                <el-input type="textarea" v-model="formData.summary" placeholder="请输入文章摘要"
                    maxlength="1000" show-word-limit :rows="4"/>
            </el-form-item>
            <el-form-item label="标签" prop="tags">
                <el-select v-model="formData.tagArray" placeholder="请输入文章标签(逗号分隔)"
                    multiple filterable allow-create style="width:100%"
                    >
                    <el-option v-for="tag in commonTags" :key="tag" :label="tag" :value="tag"></el-option>
                </el-select>
            </el-form-item>
            <el-form-item label="封面图片"><!--prop不需要加，因为上传部分自定义，没必要和原数据做关联-->
                <div class="cover-upload">
                    <el-upload
                        class="avatar-uploader"
                        action="#"
                        :before-upload="beforeUpload"
                        :http-request="handleUploadRequest"
                        :show-file-list="false"
                        accept="image/*"
                        >
                            <div v-if="!imgUrl" class="cover-placeholder">
                                <p>点击上传封面</p>
                            </div>
                            <img v-else style="width:160px;" :src="imgUrl" class="cover-image" alt="封面图片">
                    </el-upload>
                    <div v-if="imgUrl" class="cover-remove">
                        <el-button type="danger" size="small" @click="handleRemove">移除封面</el-button>
                    </div>
                </div>
            </el-form-item>
            <el-form-item labe="文章内容" prop="content">
                <rich-text-editor
                    v-model="formData.content"
                    placeholder="请输入文章内容，支持富文本格式\n\n可以使用加粗、斜体、列表、标题等格式来丰富文章内容"
                    :maxCharCount="5000"
                    @change="handleContentChange"
                    @created="handleEditorCreated"
                    min-height="300px"/>
            </el-form-item>
        </el-form>
        <div v-show="btnPreview">
            <h3>内容预览</h3>
            <div v-html="formData.content"></div>
        </div>
        <template #footer>
            <el-button @click="btnPreview=!btnPreview">{{ btnPreview?'隐藏预览':'预览效果' }}</el-button>
            <el-button @click="handleClose">取消</el-button>
            <el-button @click="handleSubmit" type="primary" :loading="loading">{{isEdit?'更新':'创建'}}</el-button>
        </template>
    </el-dialog>
</template>

<script setup>
import {ref,reactive,computed,nextTick,watch} from 'vue'
import {ElMessage} from 'element-plus'
import {uploadFile,createArticle,updateArticle} from '@/api/admin'
import RichTextEditor from '@/components/RichTextEditor.vue'
const businessId=ref(null)
const props=defineProps({
    modelValue:{
        type:Boolean,
        default:false
    },
    categories:{
        type:Array,
        default:()=>[]
    },
    article:{
        type:Object,
        default:null//弹窗中要创建字段判断新增/编辑
    }
})
const emit=defineEmits(['update:modelValue','success'])
const dialogVisible=computed({
    get(){
        return props.modelValue
    },
    set(val){
        emit('update:modelValue',val)
    }
})
const isEdit=computed(()=>!!props.article?.id)

watch(()=>props.article,(newVal)=>{
    if(newVal){
        nextTick(()=>{
            Object.assign(formData,newVal)
            //后端给的 tagList 就是拆好的数组，直接拿来给编辑框。
            //不填的话 tagArray 一直是空数组，提交时 tags=tagArray.join(',') 会把原标签整个清空
            formData.tagArray = newVal.tagList || []
            businessId.value=newVal.id
            imgUrl.value=newVal.coverImageUrl||''
        })
    }
})
const formData=reactive({
    title:"",
    content:"",
    coverImage:"",
    categoryId:1,
    summary:"",
    tags:"",
    tagArray:[],
    id:""
})
const rules=reactive({
    title:[
        {required:true,message:'请输入文章标题',trigger:'blur'},
        {max:200,message:'文章标题最多200个字符',trigger:'blur'}
    ],
    categoryId:[
        {required:true,message:'请选择分类',trigger:'change'}
    ],
    content:[
        {required:true,message:'请输入文章内容',trigger:'blur'},
        {max:5000,message:'文章内容最多5000个字符',trigger:'blur'}
    ]
})
const handleClose=()=>{
    emit('update:modelValue',false)
    businessId.value=null
    handleRemove()
    formData.tagArray=[]
    formRef.value.resetFields()
}
const commonTags = [
  '情绪管理', '焦虑', '抑郁', '压力', '睡眠', 
  '冥想', '正念', '放松', '心理健康', '自我成长',
  '人际关系', '工作压力', '学习方法', '生活技巧'
]
const imgUrl=ref('')
const beforeUpload=(file)=>{
    const isImage=file.type.startsWith('image/')
    if(!isImage){
        ElMessage.error('上传图片,请选择图片文件')
        return false
    }
    const isLt10M=file.size/1024/1024<10
    if(!isLt10M){
        ElMessage.error('上传图片大小不能超过10MB')
        return false
    }
    return true
}
const handleUploadRequest=async({file})=>{//解构回调参数中的file
    //UUID生成
    businessId.value=crypto.randomUUID()
    const fileRes=await uploadFile(file,{
        businessId:businessId.value      //必须 .value，传 ref 对象会被 FormData 变成 "[object Object]"
    })
    //预览用后端返回的完整 URL；入库的仍然是相对路径
    imgUrl.value=fileRes.fileUrl||''
    formData.coverImage=fileRes.filePath
}
const handleRemove=()=>{
    imgUrl.value=''
    formData.coverImage=''
}

//富文本
const handleContentChange=(data)=>{
    formData.content=data.html
}
//富文本创建时
const editorInstance=ref(null)
const handleEditorCreated=(editor)=>{
    editorInstance.value=editor
    if(formData.content){
        nextTick(()=>{
            editorInstance.value.setHtml(formData.content)
        })
    }
}

const btnPreview=ref(false)

const formRef=ref()
const loading=ref(false)
const handleSubmit=()=>{
    if(!formRef.value) return
    formRef.value.validate((valid,fields)=>{
        //校验不通过直接返回，否则下面的提交代码在 if 外面，标题为空也会往后端发请求
        if(!valid){
            return
        }
        loading.value=true
        const submitData={...formData,tags:formData.tagArray.join(',')}
        submitData.id=businessId.value
        delete submitData.tagArray
        if(!isEdit.value){
            submitData.id=businessId.value
            createArticle(submitData).then(res=>{
                loading.value=false
                emit('success')
                ElMessage.success('创建成功')
                dialogVisible.value=false
                Object.assign(formData,{title:"",content:"",coverImage:"",categoryId:1,summary:"",tags:"",tagArray:[],id:""})
            })
        }else{
            updateArticle(props.article.id,submitData).then(res=>{
                loading.value=false
                emit('success')
            })
        }
    })
}
</script>

<style lang="scss" scoped>
.cover-placeholder{
    width:200px;
    height:120px;
    display:flex;
    flex-direction:column;
    align-items:center;
    justify-content:center;
    color:#8b949e;
    background:#f6f8fa;
}
</style>