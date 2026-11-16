import { Alert, Button, Descriptions, Input, Message, Modal, Space } from '@arco-design/web-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api, getApiMessage, notifyApiError } from '../../lib/api'
import { copyText, QueryState, readApi, StateTag, useActionKeys, useDraftGuard } from './shared'
import type { ActionBody, DeliveryView, SecretView } from './types'
/** 凭证通过直接POST取得，绝不进入React Query mutation/query缓存或浏览器存储。 */
export function DeliveryPanel({
  requestId,
  onSensitiveChange,
}: {
  requestId: number
  onSensitiveChange: (active: boolean) => void
}) {
  const client = useQueryClient(),
    key = useActionKeys(),
    query = useQuery({
      queryKey: ['onboarding', 'delivery', requestId],
      queryFn: () => readApi<DeliveryView>(`/me/app-deliveries/${requestId}`),
    })
  const [secret, setSecret] = useState<SecretView | null>(null),
    [revealing, setRevealing] = useState(false),
    [failure, setFailure] = useState<unknown>(),
    [visibleSecret, setVisibleSecret] = useState(false)
  useEffect(() => {
    onSensitiveChange(secret !== null)
    return () => onSensitiveChange(false)
  }, [secret, onSensitiveChange])
  useEffect(() => {
    if (!secret) return
    const clear = () => {
      setSecret(null)
      setVisibleSecret(false)
    }
    const hide = () => {
      if (document.hidden) clear()
    }
    const timer = setTimeout(clear, 180000)
    document.addEventListener('visibilitychange', hide)
    window.addEventListener('pagehide', clear)
    const sessionChange = (event: StorageEvent) => {
      if (
        event.key === 'seeex.employee_id' ||
        event.key === 'seeex.tenant_id' ||
        (event.key === 'seeex.access_token' && !event.newValue)
      )
        clear()
    }
    window.addEventListener('storage', sessionChange)
    return () => {
      clearTimeout(timer)
      document.removeEventListener('visibilitychange', hide)
      window.removeEventListener('pagehide', clear)
      window.removeEventListener('storage', sessionChange)
    }
  }, [secret])
  const acknowledge = useMutation({
    mutationFn: (body: ActionBody) => api.post(`/me/app-deliveries/${requestId}/acknowledge`, body),
    onSuccess: () => {
      Message.success('已确认收件')
      void client.invalidateQueries({ queryKey: ['onboarding'] })
    },
    onError: notifyApiError,
  })
  useDraftGuard(secret !== null, revealing || acknowledge.isPending)
  async function redeem() {
    const row = query.data
    if (!row || revealing) return
    const body = { expectedVersion: row.rowVersion, deliveryId: row.id }
    const identity = ['seeex.employee_id', 'seeex.tenant_id'].map((name) => localStorage.getItem(name))
    setRevealing(true)
    setFailure(undefined)
    try {
      const response = await api.post<{ data: SecretView }>(`/me/app-deliveries/${requestId}/redeem`, body, {
        headers: { 'Idempotency-Key': key('REDEEM:' + requestId, body) },
      })
      if (
        document.hidden ||
        identity.some(
          (value, index) => value !== localStorage.getItem(['seeex.employee_id', 'seeex.tenant_id'][index]),
        )
      ) {
        setFailure(
          new Error('领取响应到达时页面已隐藏或身份已变化，凭证已清除；未保存时请联系平台轮换补发。'),
        )
      } else setSecret(response.data.data)
      setVisibleSecret(false)
      void client.invalidateQueries({ queryKey: ['onboarding'] })
    } catch (error) {
      setFailure(error)
      notifyApiError(error)
      void query.refetch()
    } finally {
      setRevealing(false)
    }
  }
  function closeSecret() {
    Modal.confirm({
      title: '已安全保存两类凭证？关闭后不能回查。',
      onOk: () => {
        setSecret(null)
        setVisibleSecret(false)
      },
    })
  }
  return (
    <CardBody>
      <QueryState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data && (
          <>
            <Space>
              <StateTag state={query.data.state} />
              <span>交付版本 {query.data.deliveryVersion}</span>
            </Space>
            <Descriptions
              className="section-card"
              column={1}
              border
              data={[
                { label: '应用编码', value: query.data.appCode },
                { label: 'Web入口', value: query.data.config.baseUrl },
                { label: '后台入口', value: query.data.config.adminUrl || '未申请' },
                { label: '服务地址', value: query.data.config.apiUrl || '未填写' },
                { label: 'SSO回调', value: query.data.config.ssoCallbackPath },
                { label: '接入说明', value: query.data.config.integrationNotes || '无' },
                { label: '领取截止', value: query.data.expiresAt },
                { label: '领取时间', value: query.data.redeemedAt || '尚未领取' },
                { label: '确认时间', value: query.data.acknowledgedAt || '尚未确认' },
              ]}
            />
            <Alert content="CI Token 用于路由上报，应用服务凭证用于后端SSO交换与权限接口。平台首次交付不代表目标应用已经完成部署或授权联调。" />
            <p>接入顺序：保存凭证 → 实现服务端SSO交换和权限判定 → 上报路由并发布 → 配置用户资源权限。</p>
            <Space wrap>
              {query.data.allowedActions.includes('REDEEM') && (
                <Button
                  type="primary"
                  loading={revealing}
                  disabled={query.isFetching || acknowledge.isPending || secret !== null}
                  onClick={() =>
                    Modal.confirm({
                      title: '领取一次性接入凭证？',
                      content:
                        '仅显示在本次弹窗。关闭、切换页面或3分钟后清除；响应丢失不能重领，需要平台轮换补发。',
                      onOk: redeem,
                    })
                  }
                >
                  领取敏感信息
                </Button>
              )}
              {query.data.allowedActions.includes('ACKNOWLEDGE') && (
                <Button
                  type="primary"
                  loading={acknowledge.isPending}
                  disabled={query.isFetching || revealing || secret !== null}
                  onClick={() => {
                    const row = query.data!
                    Modal.confirm({
                      title: '确认已经安全保存接入信息和凭证？',
                      content: '此操作确认收件，不会替代目标应用接入验收。',
                      onOk: () =>
                        acknowledge.mutateAsync({ expectedVersion: row.rowVersion, deliveryId: row.id }),
                    })
                  }}
                >
                  确认收件
                </Button>
              )}
              <Button disabled={revealing || acknowledge.isPending} onClick={() => void query.refetch()}>
                刷新交付状态
              </Button>
            </Space>
            {['EXPIRED', 'REDEEMED'].includes(query.data.state) && (
              <Alert
                className="section-card"
                type="warning"
                content="原凭证不能再次显示。如果未保存或领取响应丢失，请联系平台办理人补发；补发将立即轮换旧凭证。"
              />
            )}
          </>
        )}
        {Boolean(failure) && <Alert type="error" content={getApiMessage(failure)} />}
      </QueryState>
      <Modal
        className="onboarding-modal"
        visible={secret !== null}
        title="本人接入凭证 · 一次性显示"
        maskClosable={false}
        footer={null}
        onCancel={closeSecret}
      >
        <Alert
          type="warning"
          content="分别保存至CI Secret与应用后端Secret；关闭、页面隐藏或3分钟后清除。勿放入业务说明、日志或前端代码。"
        />
        <Space className="section-card">
          <Button onClick={() => setVisibleSecret((old) => !old)}>
            {visibleSecret ? '遮罩凭证' : '显示凭证'}
          </Button>
        </Space>
        {(['ciToken', 'clientSecret'] as const).map((name) => (
          <div key={name} className="section-card">
            <strong>{name === 'ciToken' ? 'CI Token（路由上报）' : '应用服务凭证（仅后端）'}</strong>
            <Input.TextArea
              aria-label={name}
              readOnly
              value={visibleSecret ? (secret?.[name] ?? '') : '••••••••••••••••'}
              autoSize
            />
            <Button onClick={() => secret && void copyText(secret[name])}>
              复制{name === 'ciToken' ? ' CI Token' : '服务凭证'}
            </Button>
          </div>
        ))}
        <Button long onClick={closeSecret}>
          已保存，清除并关闭
        </Button>
      </Modal>
    </CardBody>
  )
}
function CardBody({ children }: { children: import('react').ReactNode }) {
  return <div className="section-card">{children}</div>
}
