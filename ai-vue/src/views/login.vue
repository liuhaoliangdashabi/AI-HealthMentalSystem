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
            >
                <el-form-item label="用户名或邮箱" prop="username">
                    <el-input v-model="formData.username" size="large" placeholder="请输入用户名或邮箱"></el-input>
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="formData.password" size="large" placeholder="请输入密码" type="password" show-password></el-input>
                </el-form-item>
                <el-button class="btn" size="large" type="primary" @click="submitForm(ruleFormRef)">
                    登录
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
    await formEl.validate((valid,fields)=>{
        if(valid){
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
            })
        }
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