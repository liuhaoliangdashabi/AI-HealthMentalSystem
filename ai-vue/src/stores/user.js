import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser } from '@/api/frontend'

/**
 * 全局用户信息。
 *
 * 为什么需要这个 store：顶栏 Navbar 和个人主页 profile 各自维护一份 userInfo 的副本，
 * 在个人主页换完头像后，顶栏那份根本不知道，只能刷新页面才变。
 * 放进 store 之后两边读的是同一个 ref —— 谁改了，另一个立刻跟着渲染。
 */
export const useUserStore = defineStore('user', () => {
    const userInfo = ref({})
    const loaded = ref(false)

    //进行中的请求。Navbar 和 profile 会在同一帧里先后 mount、都调 load()，
    //不共享这个 promise 的话每次进页面都要多打一次 /user/current
    let loadingPromise = null

    /**
     * 拉取用户信息。先用 localStorage 顶上（避免首屏空白），再静默请求 /user/current 对齐。
     * force=true 时无视缓存重新拉。
     */
    const load = async (force = false) => {
        if (!force) {
            if (loaded.value) return userInfo.value
            if (loadingPromise) return loadingPromise
        }

        loadingPromise = (async () => {
            try {
                userInfo.value = JSON.parse(localStorage.getItem('userInfo')) || {}
            } catch (e) {
                userInfo.value = {}
            }

            try {
                const me = await getCurrentUser()
                if (me) {
                    userInfo.value = { ...userInfo.value, ...me }
                    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
                }
            } catch (e) {
                //token 过期之类，保持缓存里的样子就行
            }
            loaded.value = true
            return userInfo.value
        })()

        try {
            return await loadingPromise
        } finally {
            loadingPromise = null
        }
    }

    /**
     * 局部更新。换头像 / 改资料之后调它 —— 这就是"回调"：
     * 谁改的数据谁负责通知，顶栏订阅同一个 ref 就自动变了。
     */
    const setUser = (patch) => {
        userInfo.value = { ...userInfo.value, ...patch }
        localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    }

    const clear = () => {
        userInfo.value = {}
        loaded.value = false
        localStorage.removeItem('userInfo')
    }

    return { userInfo, loaded, load, setUser, clear }
})
