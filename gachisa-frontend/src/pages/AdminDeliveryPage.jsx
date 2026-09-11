import { useState } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import TextField from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import { updateDeliveryStatusByAdmin } from '../api/orderApi.js'

export default function AdminDeliveryPage() {
  const [orderNumber, setOrderNumber] = useState('')
  const [deliveryStatus, setDeliveryStatus] = useState('SHIPPING')
  const [message, setMessage] = useState('')
  const [isError, setIsError] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    try {
      await updateDeliveryStatusByAdmin(orderNumber, deliveryStatus)
      setIsError(false)
      setMessage('배송 상태를 변경했습니다.')
    } catch (requestError) {
      setIsError(true)
      setMessage(requestError.response?.data?.message ?? '배송 상태를 변경하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box sx={{ maxWidth: 480, mx: 'auto' }}>
      <Typography variant="h5" fontWeight={800} gutterBottom>
        관리자 배송 상태 관리
      </Typography>
      <Paper component="form" onSubmit={submit} sx={{ p: 4, mt: 2 }}>
        <Stack spacing={2}>
          {message && <Alert severity={isError ? 'error' : 'success'}>{message}</Alert>}
          <TextField
            label="주문번호"
            placeholder="예: 018330029"
            required
            value={orderNumber}
            onChange={(event) => setOrderNumber(event.target.value.replace(/\D/g, '').slice(0, 9))}
            inputProps={{ inputMode: 'numeric', maxLength: 9 }}
            fullWidth
          />
          <TextField
            select
            label="배송 상태"
            value={deliveryStatus}
            onChange={(event) => setDeliveryStatus(event.target.value)}
            fullWidth
          >
            <MenuItem value="PREPARING">배송 준비</MenuItem>
            <MenuItem value="SHIPPING">배송 중</MenuItem>
            <MenuItem value="DELIVERED">배송 완료</MenuItem>
            <MenuItem value="CANCELLED">주문 취소</MenuItem>
            <MenuItem value="RETURNING">반품 중</MenuItem>
            <MenuItem value="RETURNED">반품 완료</MenuItem>
          </TextField>
          <Button type="submit" variant="contained" size="large" disabled={submitting}>
            상태 변경
          </Button>
        </Stack>
      </Paper>
    </Box>
  )
}
