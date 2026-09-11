import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import MenuItem from '@mui/material/MenuItem'
import { getDelivery, getMyOrder, registerDeliveryAddress } from '../api/orderApi.js'
import { getSavedDeliveryAddresses } from '../api/savedDeliveryAddressApi.js'

export default function DeliveryAddressPage() {
  const { orderId } = useParams()
  const navigate = useNavigate()
  const [savedAddresses, setSavedAddresses] = useState([])
  const [selectedAddressId, setSelectedAddressId] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [loadingAddresses, setLoadingAddresses] = useState(true)
  const [alreadyRegistered, setAlreadyRegistered] = useState(false)

  useEffect(() => {
    Promise.all([getDelivery(orderId), getSavedDeliveryAddresses()])
      .then(([deliveryResponse, addressesResponse]) => {
        if (deliveryResponse.data.address) {
          setAlreadyRegistered(true)
          return
        }
        const addresses = addressesResponse.data
        setSavedAddresses(addresses)
        if (addresses.length === 1) setSelectedAddressId(String(addresses[0].id))
      })
      .catch(() => setError('저장된 배송지를 불러오지 못했습니다.'))
      .finally(() => setLoadingAddresses(false))
  }, [orderId])

  const submit = async (event) => {
    event.preventDefault()
    const selected = savedAddresses.find((saved) => saved.id === Number(selectedAddressId))
    if (!selected) {
      setError('사용할 배송지를 선택해주세요.')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await registerDeliveryAddress(orderId, {
        recipientName: selected.recipientName,
        recipientPhone: selected.recipientPhone,
        zipCode: selected.zipCode,
        address: selected.address,
        addressDetail: selected.addressDetail,
        deliveryRequest: selected.deliveryRequest ?? '',
      })
      const { data: order } = await getMyOrder(orderId)
      navigate(`/my/participations/${order.participationId}`, { replace: true })
    } catch (requestError) {
      setError(requestError.response?.data?.message ?? '배송지를 등록하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box sx={{ maxWidth: 480, mx: 'auto' }}>
      <Paper component="form" onSubmit={submit} sx={{ p: 4 }}>
        <Typography variant="h5" fontWeight={800} gutterBottom>
          배송지 입력
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 3 }}>
          배송지는 미리 등록할 수 있으며, 공동구매가 성공적으로 마감된 후 상품 준비가 시작됩니다.
        </Typography>

        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          {alreadyRegistered ? (
            <>
              <Alert severity="info">이미 배송지가 등록된 주문입니다. 기존 배송지는 변경할 수 없습니다.</Alert>
              <Button
                variant="contained"
                size="large"
                onClick={async () => {
                  const { data: order } = await getMyOrder(orderId)
                  navigate(`/my/participations/${order.participationId}`)
                }}
              >
                주문 상세로 이동
              </Button>
            </>
          ) : !loadingAddresses && savedAddresses.length === 0 ? (
            <>
              <Alert severity="info">등록된 배송지가 없습니다. 먼저 마이페이지에서 배송지를 추가해주세요.</Alert>
              <Button
                variant="contained"
                size="large"
                onClick={() => navigate(`/my/page?returnTo=${encodeURIComponent(`/orders/${orderId}/delivery-address`)}`)}
              >
                배송지 추가
              </Button>
            </>
          ) : (
            <>
              <TextField
                select
                label="저장된 배송지 선택"
                value={selectedAddressId}
                onChange={(event) => setSelectedAddressId(event.target.value)}
                disabled={loadingAddresses}
                helperText="새 배송지 등록·수정·삭제는 마이페이지에서 할 수 있습니다."
              >
                {savedAddresses.map((saved) => (
                  <MenuItem key={saved.id} value={saved.id}>
                    {saved.addressName} · {saved.recipientName} · {saved.address}
                  </MenuItem>
                ))}
              </TextField>
              {selectedAddressId && (() => {
                const selected = savedAddresses.find((saved) => saved.id === Number(selectedAddressId))
                return selected ? (
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Typography fontWeight={700}>{selected.recipientName} · {selected.recipientPhone}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      ({selected.zipCode}) {selected.address} {selected.addressDetail}
                    </Typography>
                  </Paper>
                ) : null
              })()}
              <Button type="submit" variant="contained" size="large" disabled={submitting || !selectedAddressId} sx={{ py: 1.4 }}>
                {submitting ? '등록 중...' : '선택한 배송지 사용'}
              </Button>
            </>
          )}
        </Stack>
      </Paper>
    </Box>
  )
}
