<template>
    <div class="navbar">
        <div class="flex-box">
            <el-button @click="handleCollapse">
                <el-icon><Expand/></el-icon>
            </el-button>
            <p class="page-title">{{route.meta.title}}</p>
        </div>
        <div class="flex-box">
            <el-dropdown @command="handleCommand" trigger="hover" placement="bottom-end">
                <div class="user-trigger">
                    <el-avatar :size="34" :src="avatarUrl" class="user-avatar">{{ initial }}</el-avatar>
                    <span class="user-name">{{ displayName }}</span>
                    <el-icon class="arrow"><ArrowDown/></el-icon>
                </div>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                        <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
        </div>
    </div>
</template>

<script setup>
import {computed,onMounted} from 'vue'
import { logout } from '@/api/admin'
import { useAdminStore } from '@/stores/admin'
import { useUserStore } from '@/stores/user'
import { useRoute,useRouter } from 'vue-router'
import { ElMessage,ElMessageBox } from 'element-plus'
const route=useRoute()
const router=useRouter()

//直接读 store 里的 ref：profile 页面换完头像调 setUser，这里立刻跟着变，不用刷新页面
const userStore=useUserStore()
const avatarUrl=computed(()=>userStore.userInfo.avatar||'')
const displayName=computed(()=>userStore.userInfo.nickname||userStore.userInfo.username||'管理员')
const initial=computed(()=>{
    const n=displayName.value
    return n?n.charAt(0).toUpperCase():'A'
})

const handleCommand=(command)=>{
    if(command==='profile'){
        router.push('/back/profile')
        return
    }
    if(command==='logout'){
        ElMessageBox.confirm('确定退出登录吗？','提示',{
            confirmButtonText:'确定',
            cancelButtonText:'取消',
            type:'warning'
        }).then(()=>{
            logout().then(()=>{
                userStore.clear()
                localStorage.removeItem('token')
                ElMessage.success('成功退出登录');
                router.push('/auth/login')
            })
        }).catch(()=>{
            ElMessage.info('已取消退出登录')
        })
    }
}

const handleCollapse=()=>{
    useAdminStore().toggleCollapse()
}

onMounted(()=>{
    userStore.load()
})
</script>

<style lang="scss" scoped>
.navbar{
    height:100%;
    display:flex;
    align-items:center;
    justify-content:space-between;
    padding:0 25px;
    background:white;
    box-shadow:0 1px 4px rgba(0,21,41,0.08);
    border-bottom:1px solid #e5e7eb;
    .flex-box{
        display:flex;
        align-items:center;
        justify-content:center;
    }
    .page-title{
        margin-left:20px;
        font-size:26px;
        font-weight:bold;
        color:#1f2937;
    }
    .user-trigger{
        display:flex;
        align-items:center;
        gap:8px;
        padding:4px 8px;
        border-radius:8px;
        cursor:pointer;
        transition:background 0.15s;
        &:hover{
            background:#f3f4f6;
        }
        .user-avatar{
            background:#7ED321;
            color:#fff;
            font-size:14px;
        }
        .user-name{
            font-size:14px;
            color:#374151;
            max-width:120px;
            overflow:hidden;
            text-overflow:ellipsis;
            white-space:nowrap;
        }
        .arrow{
            font-size:12px;
            color:#9ca3af;
        }
    }
}

</style>