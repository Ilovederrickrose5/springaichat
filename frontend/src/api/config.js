// API 配置文件：可通过 VITE_API_BASE_URL 覆盖（部署到其他域名时使用）
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

export default BASE_URL;
