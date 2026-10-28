<template>
    <div class="container">
        <div class="title">
            <div class="back-home">
                <el-icon><Back/></el-icon>
                <span>返回首页</span>
            </div>
            <div class="title-text">
                <h2>登陆您的账户</h2>
                <p>请输入您的登录信息</p>
            </div>
        </div>
        <div class="form-container">
            <el-form
                ref="ruleFormRef"
                :model="formData"
                :rules="rules"
                label-position="top"
                @keyup.enter="submitForm(ruleFormRef)"
            >
                <el-form-item label="用户名或邮箱" prop="username">
                    <el-input v-model="formData.username" size="large" placeholder="请输入用户名或邮箱"></el-input>
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="formData.password" size="large" placeholder="请输入密码" type="password" show-password></el-input>
                </el-form-item>
                <el-button class="btn" size="large" type="primary" :loading="loading" @click="submitForm(ruleFormRef)">
                    {{ loading ? '登录中...' : '登录' }}
                </el-button>
            </el-form>
            <div class="footer">
                <p>还没有账户？<router-link to="/auth/register">去注册</router-link></p>
            </div>
        </div>
    </div>
</template>

<script setup>
import {ref,reactive} from 'vue'
import {ElMessage} from 'element-plus'
import {login} from '@/api/admin'
import router from '@/router'
const ruleFormRef=ref()
// 提交锁：true 表示正在提交，用来挡掉连按回车 / 连点按钮
const loading=ref(false)
const formData= reactive({
    username:'',
    password:''
})
const rules= reactive({
    username:[
        {required:true,message:'请输入用户名',trigger:'blur'}
    ],
    password:[
        {required:true,message:'请输入密码',trigger:'blur'}
    ]
})

const submitForm=async(formEl)=>{
    if(!formEl)return
    // 一进门就上锁，不能等校验通过再上
    // 因为 validate 是异步的，按住回车时两次 keyup 间隔只有几十毫秒，
    // 第一次还没走到加锁那行，第二次就已经进来了 —— 锁等于没加
    if(loading.value)return
    loading.value=true

    await formEl.validate((valid,fields)=>{
        if(!valid){
            // 校验没过，立刻解锁，否则按钮一直转圈
            loading.value=false
            return
        }
        login(formData).then(data=>{
            if(!data.token){
                return console.error('登陆失败')
            }else{
                ElMessage.success('登录成功')
                localStorage.setItem('token',data.token)
                localStorage.setItem('userInfo',JSON.stringify(data.userInfo))
                //根据roleType决定跳转的路径
                if(data.userInfo.userType==2){
                    router.push('/back')
                }else{
                    router.push('/')
                }
            }
        }).catch((err)=>{
            // 错误原因已由响应拦截器统一弹出，这里仅记录
            console.error('登录失败',err)
            ElMessage.error('登录失败')
        }).finally(()=>{
            // 成功、失败、中途 return 都要解锁，所以放 finally
            loading.value=false
        })
    })
}
</script>

<style scoped lang="scss">
.container{
    width:384px;
    .title{
        margin-bottom:30px;
        .back-home{
            margin-bottom:60px;
        }
        .title-text{
            text-align:center;
            h2{
                font-size:36px;
                margin-bottom:10px;
            }
            p{
                font-size:18px;
                color:#6b7280;
            }
        }
    }
    .form-container{
        margin-top:30px;
        .btn{
            margin-top:40px;
            width:100%;
        }
        .footer{
            padding:30px;
            text-align:center;
        }
    }
}
</style>