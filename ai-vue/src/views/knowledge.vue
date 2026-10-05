<template>
    <div>
        <PageHead title="知识文章">
            <template #buttons>
                <el-button @click="handleEdit({})" type="primary">新增</el-button>
            </template>
        </PageHead>
        <TableSearch :formItem="formItem" @search="handleSearch"/>
        <el-table :data="tableData" style="width:100%;margin-top:25px">
            <el-table-column label="文章标题" fixed="left">
                <template #default="scope">
                    <div style="display:flex;align-items:center">
                        <el-icon><timer/></el-icon>
                        <span>{{ scope.row.title }}</span>
                    </div>
                </template>
            </el-table-column>
            <el-table-column label="分类" width="200">
                <template #default="scope">
                    <div style="display:flex;align-items:center">
                        <el-icon><timer/></el-icon>
                        <span>{{ categoryMap[scope.row.categoryId] }}</span>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="authorName" label="作者" width="150"/>
            <el-table-column prop="readCount" label="阅读量" width="120"/>
            <el-table-column prop="publishedAt" label="发布时间" width="150"/>
            <el-table-column label="操作" width="170" fixed="right">
                <template #default="scope">
                    <el-button @click="handleEdit(scope.row)" class="geren-btn-minpadding" text type="primary">编辑</el-button>
                    <el-button @click="handlePublish(scope.row)" class="geren-btn-minpadding" v-if="scope.row.status===0||scope.row.status===2" text type="success">发布</el-button>
                    <el-button @click="handleUnpublish(scope.row)" class="geren-btn-minpadding" v-if="scope.row.status===1" text type="warning">下线</el-button>
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
        <ArticleDialog v-model:modelValue="dialogVisible" :article="currentArticle" :categories="categories" @success="handleSuccess"/>
    </div>
</template>

<script setup>
import {ref,reactive,onMounted} from 'vue'
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue';
import {categoryTree,articlePage,getArticleDetail,changeArticleStatus} from '@/api/admin'
import {deleteArticle} from '@/api/admin'
import ArticleDialog from '@/components/ArticleDialog.vue'
import {ElMessage,ElMessageBox} from 'element-plus'

//必须是 reactive：下面 onMounted 里要给 formItem[1] 动态塞 options，
//普通数组改属性不会触发重新渲染，下拉框会是空的
const formItem=reactive([
    {comp:'input',prop:'title',label:'标题',placeholder:'请输入文章标题'},
    {comp:'select',prop:'categoryId',label:'分类',placeholder:'请选择分类'},
    {comp:'select',prop:'status',label:'状态',placeholder:'请输入文章内容',options:[
        {label:"草稿",value:'0'},
        {label:"已发布",value:'1'},
        {label:"已下线",value:'2'}
    ]}
])

const handleChange=(page)=>{
    pagination.currentPage=page
    handleSearch()
}

const categoryMap=reactive([])
const categories=ref([])
const tableData=ref([])
const dialogVisible=ref(false)
const currentArticle=ref(null)
const handleSuccess=()=>{
    dialogVisible.value=false
    handleSearch()
}
const handleEdit=(row)=>{
    if(row.id){//编辑
        getArticleDetail(row.id).then(res=>{
          currentArticle.value=res
          dialogVisible.value=true
        })
    }else{//新增
        currentArticle.value=null
        dialogVisible.value=true
    }
}

const handlePublish=(row)=>{
    ElMessageBox.confirm(
        `确认发布文章${row.title}吗？`,
        '确认',{
            confirmButtonText:'确认发布',
            cancelButtonText:'取消',
            type:'warning'
        }
    ).then(()=>{
        changeArticleStatus(row.id,{status:1}).then(res=>{
            ElMessage.success('发布成功')
            handleSearch()
        })
    })
}
const handleUnpublish=(row)=>{
    ElMessageBox.confirm(
        `确认下线文章${row.title}吗？`,
        '确认',{
            confirmButtonText:'确认下线',
            cancelButtonText:'取消',
            type:'warning'
        }
    ).then(()=>{
        changeArticleStatus(row.id,{status:2}).then(res=>{
            ElMessage.success('下线成功')
            handleSearch()
        })
    })
}

const handleDelete=(row)=>{
    ElMessageBox.confirm(
        `确认删除${row.title}吗？`,
        '确认',{
            confirmButtonText:'确认删除',
            cancelButtonText:'取消',
            type:'danger'
        }
    ).then(()=>{
        deleteArticle(row.id).then(res=>{
            ElMessage.success('删除成功')
            handleSearch()
        })
    })
}

onMounted(async()=>{
    const data=await categoryTree()
    console.log(data,'分类树')

    categories.value=data.map(item=>{
        categoryMap[item.id]=item.categoryName
        return {
            label:item.categoryName,
            value:item.id
        }
    })
    formItem[1].options=categories.value
    handleSearch()
})
//分页参数
const pagination=reactive({
    currentPage:1,
    size:5,
    total:0
})


const handleSearch=async (formData={})=>{
    console.log("查询参数",formData)
    const params={
        ...pagination,
        ...formData
    }
    const {records,total}= await articlePage(params)
    tableData.value=records
    pagination.total=total
}
</script>

<style lang="scss" scoped>
.geren-btn-minpadding{
    padding-left:6px;
    padding-right:6px;
}
</style>