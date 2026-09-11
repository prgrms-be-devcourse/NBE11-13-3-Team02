import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'

export default function Logo({ size = 32, withText = true, textVariant = 'h6' }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.2 }}>
      <Box
        sx={{
          width: size,
          height: size,
          borderRadius: size / 3.2,
          bgcolor: 'primary.main',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <svg width={size * 0.5} height={size * 0.5} viewBox="0 0 24 24" fill="none">
          <path
            d="M4 4 V13 a8 8 0 0 0 16 0 V4"
            stroke="white"
            strokeWidth="2.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </Box>
      {withText && (
        <Typography variant={textVariant} fontWeight={800} color="text.primary" lineHeight={1}>
          같이사
        </Typography>
      )}
    </Box>
  )
}
