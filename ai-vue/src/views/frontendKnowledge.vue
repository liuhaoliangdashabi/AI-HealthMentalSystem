<template>
    <div class="knowledge-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width:50px;height:50px;margin-left:10px;"/>
                <h1>情绪日志</h1>
            </div>
        </div>
        <div class="content">
            <!-- 左侧部分 -->
            <div class="recommend-section">
                <div class="section-title">推荐阅读</div>
                <div class="recommend-list">
                    <div class="recommend-item" :key="item.id"
                        v-for="item in recommendList" @click="goToArticle(item.id)">
                        <h4>{{ item.title }}</h4>
                        <p class="read-count">
                            <el-icon><Histogram/></el-icon>
                            阅读量 {{ item.readCount }}
                        </p>
                    </div>
                </div>
            </div>
            <!-- 右侧部分 -->
            <div class="article-list">
                <div class="article-item" v-for="item in articleList"
                    @click="goToArticle(item.id)" :key="item.id">
                    <el-image style="width:160px;height:120px;flex-shrink:0;"
                        fit="cover" :src="getImage(item.coverImageUrl)"/>
                    <div class="info">
                        <div class="title">
                            <h3>{{ item.title }}</h3>
                            <el-tag Plain type="primary">{{ item.categoryName }}</el-tag>
                        </div>
                        <div :style="{marginTop:'10px'}">
                            <div class="flex-box">
                                <el-icon><Avatar/></el-icon>
                                <span>{{ item.authorName }}</span>
                            </div>
                            <div class="flex-box">
                                <el-icon><List/></el-icon>
                                <span>{{ dayjs(item.updatedAt).format('YYYY-MM-DD') }}</span>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="pagination-warpper">
                    <el-pagination
                        style="margin-top:25px"
                        :page-size="pagination.size"
                        layout="prev,pager,next"
                        :total="pagination.total"
                        @change="handleChange"
                    />
                </div>
            </div>
            
        </div>
    </div>
</template>


<script setup>
import {ref,reactive,onMounted} from 'vue'
import iconUrl from '@/assets/book.png'
import { getKnowledgeList, getHotArticles } from '@/api/frontend'
import dayjs from 'dayjs'
import {useRouter} from 'vue-router'

const router=useRouter()
const recommendList=ref([])
const pagination=reactive({
    currentPage:1,
    size:10,
    total:0
})
const articleList=ref([])

const getPageList=()=>{
    const params={
        sortField:`publishedAt`,
        sortDirection:`desc`,
        ...pagination
    }
    getKnowledgeList(params).then(res=>{
        articleList.value=res.records
        pagination.total=res.total
    })
}

//后端已经给的是完整 URL（coverImageUrl），这里只负责兜底
const getImage=(url)=>{
    return url||'https://file.itndedu.com/psychology_ai.png'
}

const goToArticle=(id)=>{
    router.push(`/knowledge/article/${id}`)
}

onMounted(()=>{
    getPageList()
    // 推荐阅读：改调「7 天热度榜」接口（后端 GET /knowledge/article/hot）
    // ⚠️ 新接口返回【数组】，不再是分页对象 —— 所以直接取 res，没有 .records
    getHotArticles().then(res=>{
        recommendList.value=res
    })
})

const handleChange=(page)=>{
    pagination.currentPage=page
    getPageList()
}
</script>


<style lang="scss" scoped>
.knowledge-container {
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    .flex-box {
        display: flex;
        align-items: center;
        span {
            margin-left: 10px;
        }
    }
    .header-section {
        background: linear-gradient(135deg, #f59e0b 0%, #8b5cf6 100%);
        color: white;
        padding: 22px;
        .header-content {
            display: flex;
            align-items: center;
            gap: 12px;
        }
    }
    .content {
        display: flex;
        align-items: flex-start;
        gap: 20px;
        margin: 0 auto;
        max-width: 1200px;
        width:100%;
        box-sizing:border-box;
        padding: 20px;
        .recommend-section {
            width: 280px;
            background: white;
            border-radius: 12px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
            padding: 15px;
            .section-title {
                font-size: 12;
                font-weight: 600;
                color: #374151;
                margin-bottom: 10px;
                display: flex;
                align-items: center;
                gap: 5px;
            }
            .recommend-list {
                display: flex;
                flex-direction: column;
                gap: 1rem;
                .recommend-item {
                    border-left: 4px solid #f59e0b;
                    padding-left: 10px;
                    cursor: pointer;
                    .read-count {
                        margin-top: 10px;
                        font-size: 12px;
                        color: #6b7280;
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                }
            }
        }
        .article-list {
            flex: 1;
            .article-item {
                background: white;
                border-radius: 12px;
                box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
                padding: 15px;
                margin-bottom: 20px;
                display: flex;
                .info {
                    margin-left: 20px;
                    flex: 1;
                    min-width: 0;
                    .title {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                }
            }
        }
    }
    .pagination-wrapper {
        display: flex;
        justify-content: center;
        padding-bottom: 30px;
    }
}
</style>