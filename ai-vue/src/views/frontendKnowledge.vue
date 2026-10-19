<template>
    <div class="knowledge-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width:50px;height:50px;margin-left:10px;"/>
                <h1>知识库</h1>
            </div>
            <el-button class="exit-btn" @click="goHome">
                <el-icon><Back/></el-icon>
                <span>退出</span>
            </el-button>
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
        <!-- 回到顶部：滚过 300px 才出现，一进页面就飘个按钮很碍眼 -->
        <transition name="back-top-fade">
            <div v-show="showBackTop" class="back-top" @click="scrollToTop">
                <el-icon :size="20"><Top/></el-icon>
            </div>
        </transition>
    </div>
</template>


<script setup>
import {ref,reactive,onMounted,onBeforeUnmount} from 'vue'
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

//退出：回首页。用 replace 不是 push —— 点了"退出"再按浏览器后退，
//不应该又弹回知识库（那就不叫退出了）
const goHome=()=>{
    router.replace('/')
}

//========== 回到顶部 ==========
const showBackTop=ref(false)
const BACK_TOP_THRESHOLD=300

const handleScroll=()=>{
    showBackTop.value=window.scrollY>BACK_TOP_THRESHOLD
}

const scrollToTop=()=>{
    window.scrollTo({top:0,behavior:'smooth'})
}

onMounted(()=>{
    getPageList()
    // 推荐阅读：改调「7 天热度榜」接口（后端 GET /knowledge/article/hot）
    // ⚠️ 新接口返回【数组】，不再是分页对象 —— 所以直接取 res，没有 .records
    getHotArticles().then(res=>{
        recommendList.value=res
    })
    window.addEventListener('scroll',handleScroll,{passive:true})
})

//组件卸载时必须摘掉监听，否则每次进这个页面都会多挂一个，来回切几次就泄漏了
onBeforeUnmount(()=>{
    window.removeEventListener('scroll',handleScroll)
})

const handleChange=(page)=>{
    pagination.currentPage=page
    getPageList()
    //翻页后回到顶部，不然还停在上一次的底部，得自己往上拖
    scrollToTop()
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
        display: flex;
        align-items: center;
        justify-content: space-between;
        .header-content {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .exit-btn {
            background: rgba(255, 255, 255, 0.18);
            border: 1px solid rgba(255, 255, 255, 0.45);
            color: #fff;
            &:hover,
            &:focus {
                background: rgba(255, 255, 255, 0.32);
                border-color: #fff;
                color: #fff;
            }
        }
    }
    .back-top {
        position: fixed;
        right: 40px;
        bottom: 80px;
        width: 46px;
        height: 46px;
        border-radius: 50%;
        background: linear-gradient(135deg, #f59e0b 0%, #8b5cf6 100%);
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        box-shadow: 0 6px 18px rgba(139, 92, 246, 0.35);
        z-index: 100;
        transition: transform 0.2s ease;
        &:hover {
            transform: translateY(-3px);
        }
    }
    .back-top-fade-enter-active,
    .back-top-fade-leave-active {
        transition: opacity 0.25s ease, transform 0.25s ease;
    }
    .back-top-fade-enter-from,
    .back-top-fade-leave-to {
        opacity: 0;
        transform: translateY(10px);
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