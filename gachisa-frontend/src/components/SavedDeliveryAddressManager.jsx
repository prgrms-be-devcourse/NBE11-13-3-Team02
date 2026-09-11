import { useEffect, useState } from 'react'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Divider from '@mui/material/Divider'
import KakaoAddressFields from './KakaoAddressFields.jsx'
import {
  createSavedDeliveryAddress,
  deleteSavedDeliveryAddress,
  getSavedDeliveryAddresses,
  updateSavedDeliveryAddress,
} from '../api/savedDeliveryAddressApi.js'

const emptyForm = {
  addressName: '', recipientName: '', recipientPhone: '', zipCode: '',
  address: '', addressDetail: '', deliveryRequest: '',
}

export default function SavedDeliveryAddressManager({ onSaved }) {
  const [addresses, setAddresses] = useState([])
  const [form, setForm] = useState(emptyForm)
  const [editingId, setEditingId] = useState(null)
  const [error, setError] = useState('')

  const load = () => getSavedDeliveryAddresses()
    .then(({ data }) => setAddresses(data))
    .catch(() => setError('배송지 목록을 불러오지 못했습니다.'))

  useEffect(() => { load() }, [])

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    try {
      if (editingId) await updateSavedDeliveryAddress(editingId, form)
      else await createSavedDeliveryAddress(form)
      const wasCreating = !editingId
      setForm(emptyForm)
      setEditingId(null)
      await load()
      if (wasCreating) onSaved?.()
    } catch (requestError) {
      setError(requestError.response?.data?.message ?? '배송지를 저장하지 못했습니다.')
    }
  }

  const edit = (address) => {
    setEditingId(address.id)
    setForm({
      addressName: address.addressName, recipientName: address.recipientName,
      recipientPhone: address.recipientPhone, zipCode: address.zipCode,
      address: address.address, addressDetail: address.addressDetail,
      deliveryRequest: address.deliveryRequest ?? '',
    })
  }

  const remove = async (id) => {
    if (!window.confirm('이 배송지를 삭제할까요?')) return
    await deleteSavedDeliveryAddress(id)
    if (editingId === id) { setEditingId(null); setForm(emptyForm) }
    await load()
  }

  return (
    <Paper sx={{ p: 3, mt: 3, maxWidth: 760 }} elevation={1}>
      <Typography variant="h6" fontWeight={700}>내 배송지</Typography>
      <Stack spacing={1.5} sx={{ my: 2 }}>
        {addresses.map((saved) => (
          <Paper key={saved.id} variant="outlined" sx={{ p: 2 }}>
            <Typography fontWeight={700}>{saved.addressName}</Typography>
            <Typography variant="body2">{saved.recipientName} · {saved.recipientPhone}</Typography>
            <Typography variant="body2" color="text.secondary">
              ({saved.zipCode}) {saved.address} {saved.addressDetail}
            </Typography>
            <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
              <Button size="small" onClick={() => edit(saved)}>수정</Button>
              <Button size="small" color="error" onClick={() => remove(saved.id)}>삭제</Button>
            </Stack>
          </Paper>
        ))}
        {addresses.length === 0 && <Typography color="text.secondary">등록한 배송지가 없습니다.</Typography>}
      </Stack>
      <Divider sx={{ my: 2 }} />
      <Typography fontWeight={700} sx={{ mb: 2 }}>{editingId ? '배송지 수정' : '새 배송지 등록'}</Typography>
      <Stack component="form" onSubmit={submit} spacing={2}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label="배송지 이름 (예: 집, 회사)" value={form.addressName}
          onChange={(e) => setForm({ ...form, addressName: e.target.value })} required />
        <TextField label="받는 사람" value={form.recipientName}
          onChange={(e) => setForm({ ...form, recipientName: e.target.value })} required />
        <TextField label="연락처" placeholder="010-1234-5678" value={form.recipientPhone}
          onChange={(e) => setForm({ ...form, recipientPhone: e.target.value })} required />
        <KakaoAddressFields form={form} setForm={setForm} onError={setError} />
        <TextField label="배송 요청사항" value={form.deliveryRequest}
          onChange={(e) => setForm({ ...form, deliveryRequest: e.target.value })} />
        <Stack direction="row" spacing={1}>
          <Button type="submit" variant="contained">{editingId ? '수정 완료' : '배송지 등록'}</Button>
          {editingId && <Button onClick={() => { setEditingId(null); setForm(emptyForm) }}>취소</Button>}
        </Stack>
      </Stack>
    </Paper>
  )
}
