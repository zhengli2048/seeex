import { Button, Card, Radio, Select, Space, TreeSelect } from '@arco-design/web-react'
import { treeNodes, type TreeRow } from '../lib/management'

export interface Membership { departmentId: number | null; jobGradeId: number | null; primary: boolean }
interface Props { value?: Membership[]; onChange?: (value: Membership[]) => void; departments: TreeRow[]; grades: { id: number; name: string }[]; disabled?: boolean }

/** 多部门表单控件，主部门用单选，TreeSelect 值保持字符串。 */
export function DepartmentMemberships({ value = [], onChange, departments, grades, disabled }: Props) {
  function change(index: number, update: Partial<Membership>) { onChange?.(value.map((row, i) => ({ ...row, ...(i === index ? update : update.primary ? { primary: false } : {}) }))) }
  return <Space direction="vertical" style={{ width: '100%' }}>{value.map((row, index) => <Card key={index} size="small">
    <Space wrap>
    <TreeSelect disabled={disabled} style={{ width: 200 }} showSearch placeholder="选择部门" value={row.departmentId == null ? undefined : String(row.departmentId)} treeData={treeNodes(departments)} onChange={(id) => change(index, { departmentId: id ? Number(id) : null })} />
    <Select disabled={disabled} style={{ width: 160 }} showSearch allowClear placeholder="该部门职级" value={row.jobGradeId ?? undefined} options={grades.map((grade) => ({ label: grade.name, value: grade.id }))} onChange={(id) => change(index, { jobGradeId: id ?? null })} />
    <Radio disabled={disabled} checked={row.primary} onChange={() => change(index, { primary: true })}>主部门</Radio>
    {!disabled && <Button type="text" status="danger" onClick={() => { const next = value.filter((_, i) => i !== index); if (row.primary && next.length) next[0] = { ...next[0], primary: true }; onChange?.(next) }}>移除</Button>}
  </Space>
    </Card>)}{!disabled && <Button onClick={() => onChange?.([...value, { departmentId: null, jobGradeId: null, primary: value.length === 0 }])}>添加所属部门</Button>}</Space>
}
