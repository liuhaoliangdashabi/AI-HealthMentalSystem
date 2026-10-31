import service from "../utils/request";
export function login(data){
    return service.post('/user/login',data)
}
export function categoryTree(){
    return service.get('/knowledge/category/tree')
}
export function articlePage(params){
    return service.get('/knowledge/article/page',{params:params})
}
export function uploadFile(file,businessInfo){
    const formData=new FormData()
    formData.append('file',file)
    formData.append('businessType','ARTICLE')
    formData.append('businessId',businessInfo.businessId)
    formData.append('businessField','cover')

    return service.post('file/upload',formData,{
        headers:{
            'content-type':'multipart/form-data'
        }
    })
}

export function createArticle(data){
    return service.post('/knowledge/article',data)
}

export function getArticleDetail(id){
    return service.get(`/knowledge/article/${id}`)
}

export function updateArticle(id,data){
    return service.put(`/knowledge/article/${id}`,data)
}

export function changeArticleStatus(id,data){
    return service.put(`/knowledge/article/${id}/status`,data)
}

export function deleteArticle(id){
    return service.delete(`/knowledge/article/${id}`)
}

export function getConsultationPage(params){
    return service.get('/psychological-chat/sessions',{params:params})
}

export function getSessionDetail(sessionId){
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

export function getEmotionalPage(params){
    //D7 起日记域不再区分 admin 路径，B/C 端共用 /emotion-diary/page，由后端按 X-User-Role 决定看谁的
    return service.get('/emotion-diary/page',{params})
}

export function deleteEmotional(id){
    return service.delete(`/emotion-diary/${id}`)
}

//手动触发一篇日记的 AI 分析（给上线前的老日记补算用）
export function analyzeDiary(id){
    return service.post(`/emotion-diary/${id}/analyze`)
}

export function getAnalyticsOverview(){
    return service.get('/data-analytics/overview')
}

export function logout(){
    return service.post('/user/logout')
}