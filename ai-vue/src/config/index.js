export const fileBaseUrl="http://localhost:9000"
// 保持/api相对路径走Vite代理（无跨域）；代理目标自动读取上面的fileBaseUrl，无需再改vite.config.js
export const apiBaseUrl="/api"