import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import { getMyOrder } from '../api/orderApi.js'
import { getErrorMessage } from '../api/errorMessage.js'
import LoadingScreen from '../components/LoadingScreen.jsx'

export default function LegacyOrderRedirectPage() {
  const { orderId } = useParams()
  const navigate = useNavigate()
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false

    getMyOrder(orderId)
      .then(({ data }) => {
        if (!cancelled) {
          navigate(`/my/participations/${data.participationId}`, { replace: true })
        }
      })
      .catch((requestError) => {
        if (!cancelled) setError(getErrorMessage(requestError, '주문 정보를 불러오지 못했습니다.'))
      })

    return () => {
      cancelled = true
    }
  }, [navigate, orderId])

  if (!error) return <LoadingScreen />

  return (
    <Stack spacing={2}>
      <Alert severity="error">{error}</Alert>
      <Button onClick={() => navigate('/my/participations', { replace: true })}>
        내 참여내역으로
      </Button>
    </Stack>
  )
}
