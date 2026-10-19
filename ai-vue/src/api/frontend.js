import service from "../utils/request";

export const register=(data)=>{
    return service.post('/user/add',data)
}

export const startSession=(data)=>{
    return service.post('/psychological-chat/session/start',data)
}

export const getSessionList=(params)=>{
    return service.get('/psychological-chat/sessions',{params})
}

export const deleteSession=(sessionId)=>{
    return service.delete(`/psychological-chat/sessions/${sessionId}`)
}

export const getSessionDetail=(sessionId)=>{
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

export const getSessionEmotion=(sessionId)=>{
    return service.get(`/psychological-chat/session/${sessionId}/emotion`)
}

export const addEmotionDiary=(data)=>{
    return service.post('/emotion-diary',data)
}

export const getMyDiaries=(params)=>{
    return service.get('/emotion-diary/page',{params})
}

export const deleteMyDiary=(id)=>{
    return service.delete(`/emotion-diary/${id}`)
}

export const updateEmotionDiary=(id,data)=>{
    return service.put(`/emotion-diary/${id}`,data)
}

export const getKnowledgeList=(params)=>{
    return service.get('/knowledge/article/page',{params})
}

export const getHotArticles=()=>{
    return service.get('/knowledge/article/hot')
}

export const getKnowledgeDetail=(articleId)=>{
    return service.get(`/knowledge/article/${articleId}`)
}

//当前登录用户的最新信息（换过头像后要靠它拿回新值，localStorage 是旧的）
export const getCurrentUser=()=>{
    return service.get('/user/current')
}

//个人主页：最近 91 天的日记（日历用）
export const getUserActivity=()=>{
    return service.get('/user/activity')
}

//更换头像。avatar 传上传接口返回的 filePath（objectKey），不是完整 URL
export const updateAvatar=(avatar)=>{
    return service.put('/user/avatar',{avatar})
}

//修改个人资料（昵称/邮箱/手机/性别/生日）。用户名和密码不走这个接口
export const updateProfile=(data)=>{
    return service.put('/user/profile',data)
}

//上传文件。默认 timeout 只有 5 秒，图片上传要放宽
//signal 用来中途取消：用户选错图了能立刻掐掉，不用干等
export const uploadFile=(formData,signal)=>{
    return service.post('/file/upload',formData,{
        headers:{'Content-Type':'multipart/form-data'},
        timeout:60000,
        signal
    })
}
