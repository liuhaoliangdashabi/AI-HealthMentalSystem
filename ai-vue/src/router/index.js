import { createRouter, createWebHistory } from 'vue-router';
import {ElMessage} from 'element-plus'
import BackendLayout from '../components/BackendLayout.vue';
import AuthorLayout from '../components/AuthorLayout.vue';
import FrontendLayout from '../components/FrontendLayout.vue';
const backendRoutes=[
    {
        path:'/back',
        component:BackendLayout,
        redirect:'/back/dashboard',
        children:[
            {
                path:'dashboard',
                component:()=>import('@/views/dashboard.vue'),
                meta:{
                    title:'数据分析',
                    icon:'PieChart'
                    
                }
            },
            {
                path:'knowledge',
                component:()=>import('@/views/knowledge.vue'),
                meta:{
                    title:'知识文章',
                    icon:'ChatLineSquare'
                }
            },
            {
                path:'consultations',
                component:()=>import('@/views/consultations.vue'),
                meta:{
                    title:'咨询记录',
                    icon:'Message'
                }
            },
            {
                path:'emotional',
                component:()=>import('@/views/emotional.vue'),
                meta:{
                    title:'情绪日志',
                    icon:'User'
                }
            }
        ]
    },{
        path:'/auth',
        component:AuthorLayout,
        redirect:'/auth/login',
        children:[
            {
                path:'login',
                component:()=>import('@/views/login.vue'),
                meta:{
                    title:'登录'
                }
            },
            {
                path:'register',
                component:()=>import('@/views/register.vue'),
                meta:{
                    title:'注册'
                }
            }
        ]
    }
]
const frontendRoutes=[
    {
        path:'/',
        component:FrontendLayout,
        children:[
            {
                path:'',
                component:()=>import('@/views/home.vue')
            },
            {
                path:'consultation',
                component:()=>import('@/views/consultation.vue')
            },{
                path:'emotion-diary',
                component:()=>import('@/views/emotionDiary.vue')
            },{
                path:'knowledge',
                component:()=>import('@/views/frontendKnowledge.vue')
            },{
                path:'knowledge/article/:id',
                component:()=>import('@/views/ArticleDetail.vue'),
                //如果想通过props获取，还需要做操作
                props:true
            }
        ]
    }
]
const router=createRouter({
    history:createWebHistory(),
    routes:[...backendRoutes,...frontendRoutes]
})

//路由前置守卫
router.beforeEach((to,from,next)=>{
    const token=localStorage.getItem('token')
    if(token){
        //userInfo 有可能丢（手动清了缓存、或者只写进了 token）
        //JSON.parse(null) 返回 null → 直接取 .userType 会 TypeError，把整个守卫搞崩 → 所有跳转失效
        let userInfo=null
        try{
            userInfo=JSON.parse(localStorage.getItem('userInfo'))
        }catch(e){
            userInfo=null
        }
        if(!userInfo||!userInfo.userType){
            //token 在但用户信息没了 —— 等于没登录，清掉残留
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            if(to.path.startsWith('/back')){
                next('/auth/login')
            }else{
                next()
            }
            return
        }
        if(userInfo.userType==2){
            if(to.path.startsWith('/back')){
                next()
            }else{
                next('/back/dashboard')
            }
        }else if(userInfo.userType==1){
            if(to.path.startsWith('/back')||to.path.startsWith('/auth')){
                next('/')
            }else{
                next()
            }
        }
    }else{
        if(to.path.startsWith('/back')){
            next('/auth/login')
            ElMessage.error('您还未登录，请先登录')
        }else{
            next()
        }
    }
})


export default router;