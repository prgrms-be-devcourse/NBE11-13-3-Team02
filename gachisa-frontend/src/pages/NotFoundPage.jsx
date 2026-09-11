import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <Box sx={{ textAlign: 'center', py: 10 }}>
      <Typography variant="h3" fontWeight={700} gutterBottom>
        404
      </Typography>
      <Typography color="text.secondary" gutterBottom>
        페이지를 찾을 수 없습니다.
      </Typography>
      <Button component={Link} to="/" variant="contained" sx={{ mt: 2 }}>
        홈으로
      </Button>
    </Box>
  )
}
