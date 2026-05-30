import { FormattedMessage as IntlFormattedMessage, useIntl } from 'react-intl'
import enMessages from '../locales/en.json'

export const messages = {
  en: enMessages,
}

export type Locale = keyof typeof messages

export const defaultLocale: Locale = 'en'

export function useI18n() {
  const intl = useIntl()

  return {
    formatMessage: (...args: Parameters<typeof intl.formatMessage>) => intl.formatMessage(...args),
    formatDate: (...args: Parameters<typeof intl.formatDate>) => intl.formatDate(...args),
    formatTime: (...args: Parameters<typeof intl.formatTime>) => intl.formatTime(...args),
    formatNumber: (...args: Parameters<typeof intl.formatNumber>) => intl.formatNumber(...args),
    formatPlural: (...args: Parameters<typeof intl.formatPlural>) => intl.formatPlural(...args),
  }
}

// Convenience wrapper for FormattedMessage with better typing
export const FormattedMessage = IntlFormattedMessage
