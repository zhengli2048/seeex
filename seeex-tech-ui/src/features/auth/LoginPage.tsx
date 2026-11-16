import {Button, Form, Input, Divider} from '@arco-design/web-react'
import {useEffect, useRef, useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {useSearchParams} from 'react-router-dom'
import {useNavigate} from 'react-router-dom'
import {api, notifyApiError} from '../../lib/api'
import {saveTokens, saveUserContext} from '../../lib/auth'

export function LoginPage() {
    const [loading, setLoading] = useState(false)
    const navigate = useNavigate()
    const [params] = useSearchParams()
    const consumed = useRef(false)
    const wecom = useQuery({ queryKey: ['wecom-login-status'], queryFn: async () => (await api.get<{ data: { enabled: boolean } }>('/auth/wecom/status')).data.data, retry: false })
    useEffect(() => {
        const code = params.get('code'); const state = params.get('state')
        if (!code || !state || consumed.current) return
        consumed.current = true
        setLoading(true)
        void api.get<{ data: { accessToken: string; refreshToken: string } }>('/auth/wecom/callback', { params: { code, state } }).then(async (response) => {
            saveTokens(response.data.data.accessToken, response.data.data.refreshToken)
            const me = await api.get('/me'); saveUserContext(me.data.data.employeeId, me.data.data.tenantId); navigate('/', { replace: true })
        }).catch((error) => notifyApiError(error)).finally(() => setLoading(false))
    }, [params, navigate])

    async function onSubmit(values: { phone: string; password: string }) {
        setLoading(true)
        try {
            const {data} = await api.post('/auth/login', values)
            saveTokens(data.data.accessToken, data.data.refreshToken)
            const me = await api.get('/me')
            saveUserContext(me.data.data.employeeId, me.data.data.tenantId)
            navigate('/')
        } catch (error) {
            notifyApiError(error, '登录失败，请检查账号和密码')
        } finally {
            setLoading(false)
        }
    }

    return <main className="login-page">
        <section className="login-shell" aria-label="SEEEX 登录">
            <div className="login-visual" aria-hidden="true">
                <div className="login-drawing"><img src="/brand/login-hero.png" alt=""/></div>
                <div className="login-visual-overlay"/>
            </div>
            <div className="login-panel">
                <div className="login-content">
                    <img src="/brand/seeex-logo-reference.png" alt="SEEEX" className="login-logo" /><p className="login-subtitle"></p>
      <Form onSubmit={onSubmit} layout="vertical" className="login-form"><Form.Item field="phone" label={<span className="nowrap-label">账号</span>} rules={[{ required: true, message: '请输入账号' }]}><Input size="large" placeholder="请输入账号" /></Form.Item><Form.Item field="password" label={<span className="nowrap-label">密码</span>} rules={[{ required: true, message: '请输入密码' }]}><Input.Password size="large" placeholder="请输入密码" /></Form.Item><Button htmlType="submit" type="primary" size="large" long loading={loading}>登录</Button></Form>
      <Divider>或</Divider><Button size="large" long disabled={!wecom.data?.enabled || loading} onClick={async () => { try { const response = await api.get<{ data: { authorizeUrl: string } }>('/auth/wecom/authorize'); window.location.assign(response.data.data.authorizeUrl) } catch (error) { notifyApiError(error) } }}>使用企业微信登录</Button>
    </div></div>
  </section></main>
}







