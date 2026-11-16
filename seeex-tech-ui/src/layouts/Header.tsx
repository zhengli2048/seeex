import { Button, Dropdown, Menu } from '@arco-design/web-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { clearAccessToken, getRefreshToken } from '../lib/auth'
import { api, notifyApiError } from '../lib/api'
export function Header({ onMobileMenu }: { onMobileMenu: () => void }) {
  const navigate = useNavigate(); const client = useQueryClient()
  const me = useQuery({ queryKey: ['me'], queryFn: async () => (await api.get<{ data: { displayName: string } }>('/me')).data.data })
  async function logout() { try { await api.post('/auth/logout', { refreshToken: getRefreshToken() }) } catch (error) { notifyApiError(error) } finally { clearAccessToken(); client.clear(); navigate('/login', { replace: true }) } }
  return <header className="header"><Button className="mobile-menu" onClick={onMobileMenu}>菜单</Button><div className="header-spacer" /><Dropdown droplist={<Menu><Menu.Item key="logout" onClick={() => void logout()}>退出登录</Menu.Item></Menu>}><Button type="text">{me.data?.displayName ?? '当前用户'} ▾</Button></Dropdown></header>
}
