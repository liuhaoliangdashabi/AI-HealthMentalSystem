<template>
    <div class="articleDetail-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width:50px;height:50px;margin-left:30px;"/>
                <h3>知识文章详情</h3>
            </div>
            <el-button class="back-btn" @click="goBack">
                <el-icon><Back/></el-icon>
                <span>返回列表</span>
            </el-button>
        </div>
        <div class="content">
            <!-- 上半部分 -->
            <div class="diary-card">
                <p class="title">文章信息</p>
                <div class="sub-title">
                    <el-tag size="large" class="category-tag">{{ articleDetail.categoryName }}</el-tag>
                    <div class="flex-box">
                        <el-icon><List/></el-icon>
                        <span>{{ dayjs(articleDetail.updatedAt).format('YYYY-MM-DD') }}</span>
                    </div>
                </div>
                <h1 class="article-title">{{ articleDetail.title }}</h1>
                <div class="summary-content" v-if="articleDetail.summary">
                    <p>{{ articleDetail.summary }}</p>
                </div>
                <div :style="{marginTop:'20px'}" class="flex-box">
                    <div class="item flex-box">
                        <el-icon><Avatar/></el-icon>
                        <span>{{ articleDetail.authorName }}</span>
                    </div>
                    <div class="item flex-box">
                        <el-icon><Platform/></el-icon>
                        <span>{{ articleDetail.readCount }}次阅读</span>
                    </div>
                </div>
            </div>
            <!-- 下半部分 -->
            <div class="diary-card">
                <div class="title">正文内容</div>
                <div class="content-wrapper" v-html="formatContent(articleDetail.content)"></div>
                <div class="tags-content" v-if="tagList.length>0">
                    <h4>相关标签</h4>
                    <div class="tags-list">
                        <el-tag v-for="tag in tagList" :key="tag"
                            class="tags-item" type="info" effect="light">
                            {{ tag }}
                        </el-tag>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>


<script setup>
import {ref,computed,onMounted} from 'vue'
import iconUrl from '@/assets/book.png'
import {getKnowledgeDetail} from '@/api/frontend'
import dayjs from 'dayjs'
import {useRouter} from 'vue-router'

const router=useRouter()

//返回列表用 push('/knowledge') 而不是 router.back()：
//直接粘贴文章链接进来的人没有"上一页"，back 会跳出站外
const goBack=()=>{
    router.push('/knowledge')
}


//url跳转时，能直接从url上读取变量
const props=defineProps({
    id:String
})
const articleDetail=ref({})

//后端直接给了拆好的 tagList（走 KnowledgeArticle.getTagList()）。
//首次渲染时 articleDetail 还是空对象，所以要兜个底，否则模板里 .length 会抛异常
const tagList=computed(()=>articleDetail.value?.tagList || [])

const formatContent = (content) => {
  if (!content) return ''
  // 基本的HTML清理和格式化
  let formatted = content
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
  return formatted
}

onMounted(()=>{
    getKnowledgeDetail(props.id).then(res=>{
        articleDetail.value=res
    })
})
</script>

<style lang="scss" scoped>
.articleDetail-container {
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    .flex-box {
        display: flex;
        align-items: center;
        .item {
            margin-right: 20px;
            span {
                margin-left: 5px;
            }
        }
    }
    .header-section {
        background: linear-gradient(135deg, #f59e0b 0%, #8b5cf6 100%);
        color: white;
        padding: 14px 20px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        .header-content {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .back-btn {
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
    .content {
        margin: 0 auto;
        width: 980px;
        padding: 20px;
        .diary-card {
            margin-bottom: 20px;
            background: white;
            border-radius: 10px;
            padding: 20px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
            .title {
                margin-bottom: 15px;
                font-size: 20px;
                font-weight: 600;
                color: #374151;
            }
            .sub-title {
                margin-top: 20px;
                display: flex;
                align-items: center;
                .category-tag {
                    margin-right: 20px;
                }
            }
            .article-title {
                font-size: 28px;
                font-weight: bold;
                color: #111827;
                margin-top: 30px;
                margin-bottom: 10px;
            }
            .summary-content {
                background: rgba(126, 211, 33, 0.1);
                border-left: 4px solid #7ED321;
                padding: 10px 15px;
                border-radius: 0 8px 8px 0;
                position: relative;
            }
            .content-wrapper {
                font-size: 15px;
                color: #374151;
                :deep(p) {
                    margin-bottom: 10px;
                }
                :deep(h1),
                :deep(h2),
                :deep(h3),
                :deep(h4),
                :deep(h5),
                :deep(h6) {
                    margin: 15px 0 10px;
                    color: #111827;
                    font-weight: 600;
                }
                :deep(h2) {
                    font-size: 15px;
                    border-bottom: 2px solid #e5e7eb;
                    padding-bottom: 5px;
                }
                :deep(h3) {
                    font-size: 13px;
                }
                :deep(ul),
                :deep(ol) {
                    padding-left: 15px;
                    margin-bottom: 10px;
                }
                :deep(li) {
                    margin-bottom: 5px;
                }
            }
            .tags-content {
                margin-top: 20px;
                padding-top: 15px;
                border-top: 1px solid #e5e7eb;
                .tags-title {
                    margin-bottom: 10px;
                    font-size: 14px;
                    font-weight: 600;
                    color: #374151;
                }
                .tags-list {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 10px;
                }
            }
        }
    }
}
</style>
