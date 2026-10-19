<template>
    <div class="container">
        <div class="title">
            <div class="title-text">
                <h2>创建您的账户</h2>
                <p>请填写注册信息</p>
            </div>
        </div>
        <div class="form-container">
            <el-form label-position="top" :model="formData" 
                :rules="rules" ref="submitRef">
                <el-form-item label="用户名或邮箱" prop="username">
                    <el-input v-model="formData.username" placeholder="请输入用户名或邮箱"/>
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                    <el-input v-model="formData.email" placeholder="请输入邮箱"/>
                </el-form-item>
                <el-form-item label="昵称" prop="nickname">
                    <el-input v-model="formData.nickname" placeholder="请输入昵称"/>
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                    <el-input v-model="formData.phone" placeholder="请输入手机号"/>
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="formData.password" placeholder="请输入密码" type="password"/>
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input v-model="formData.confirmPassword" placeholder="请再次输入密码" type="password"/>
                </el-form-item>
                <el-form-item>
                    <el-button class="btn" type="primary" 
                        size="large" @click="submitForm(submitRef)">
                        注册
                    </el-button>
                </el-form-item>
            </el-form>
        </div>
    </div>
</template>

<script setup>
import {ref,reactive,onMounted} from 'vue'
import {register} from '@/api/frontend'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
const formData=reactive({
    username:"",
    email:"",
    nickname:"",
    phone:"",
    password:"",
    confirmPassword:"",
    gender:0,
    userType:1
})
const submitRef=ref(null)
const router=useRouter()
const rules=reactive({
    username:[
        {required:true,message:'请输入用户名',trigger:'blur'}
    ],
    email:[
        {required:true,message:'请输入邮箱',trigger:'blur'}
    ],
    password:[
        {required:true,message:'请输入密码',trigger:'blur'}
    ],
    confirmPassword:[
        {required:true,message:'请确认密码',trigger:'blur'}
    ]
})


const submitForm=async(formEl)=>{
    if(!formEl)return
    if(!formData.phone)formData.phone=null
    await formEl.validate(async(valid)=>{
        register(formData).then(({data})=>{
            if(!data){
                ElMessage.success('注册成功')
                router.push('/auth/login')
                return
            }
            if(data.code==='BUSINESS_ERROR'){
                ElMessage.error(data.message)
            }
        }).catch((err)=>{
            // 错误消息已由响应拦截器统一弹出，这里仅捕获避免 unhandled rejection
            console.error('注册失败',err)
        })
    })
}
</script>

<style lang="scss" scoped>
.container {
    width: 384px;
    margin: auto;
    .flex-box {
        display: flex;
        align-items: center;
    }
    .title {
        .title-text {
            text-align: center;
            h2 {
                font-size: 36px;
                margin-bottom: 10px;
            }
            p {
                font-size: 18px;
                color: #6b7280;
            }
        }
    }
    .form-container {
        margin-top: 20px;
        :deep(.el-form-item) {
            margin-bottom: 12px;
        }
        .btn {
            margin-top: 25px;
            width: 100%;
        }
        .footer {
            padding: 25px;
            text-align: center;
        }
    }
}
</style>