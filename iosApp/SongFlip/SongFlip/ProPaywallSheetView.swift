import SwiftUI
import RevenueCat
import RevenueCatUI
import SongFlipKit

struct ProPaywallSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var proManager = ProManager.shared

    var offering: Offering? = nil

    private var lang: String {
        settings.selectedLanguage
    }

    var body: some View {
        if proManager.isPro {
            NavigationStack {
                VStack(spacing: 24) {
                    Spacer()

                    // Active PRO Status Card
                    VStack(spacing: 14) {
                        Text("🎉")
                            .font(.system(size: 48))
                        Text(LocalizationManager.string(for: "pro_active_status", lang: lang))
                            .font(.title2)
                            .fontWeight(.bold)
                            .foregroundColor(.primary)

                        let subText: String = {
                            if proManager.proType?.contains("lifetime") == true {
                                return LocalizationManager.string(for: "pro_active_lifetime", lang: lang)
                            } else if let exp = proManager.expirationDate {
                                let formatter = DateFormatter()
                                formatter.dateStyle = .medium
                                return String(
                                    format: LocalizationManager.string(for: "pro_active_annual", lang: lang),
                                    formatter.string(from: exp)
                                )
                            } else {
                                return LocalizationManager.string(for: "pro_active_lifetime", lang: lang)
                            }
                        }()

                        Text(subText)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(28)
                    .background(Color.green.opacity(0.12))
                    .cornerRadius(20)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color.green.opacity(0.4), lineWidth: 1.5)
                    )
                    .padding(.horizontal, 24)

                    Spacer()

                    Button(action: {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        dismiss()
                    }) {
                        Text(LocalizationManager.string(for: "btn_done", lang: lang))
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.green)
                            .cornerRadius(14)
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
                }
                .navigationTitle(LocalizationManager.string(for: "pro_title", lang: lang))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(action: {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            dismiss()
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .symbolRenderingMode(.hierarchical)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
        } else if !Purchases.isConfigured {
            VStack(spacing: 16) {
                Spacer()
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 44))
                    .foregroundColor(.orange)
                Text(LocalizationManager.string(for: "pro_play_store_unavailable", lang: lang))
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                Button(action: { dismiss() }) {
                    Text(LocalizationManager.string(for: "btn_ok", lang: lang))
                        .fontWeight(.semibold)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(Color.accentColor)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                Spacer()
            }
            .padding()
        } else {
            Group {
                if let offering = offering {
                    PaywallView(offering: offering, displayCloseButton: true)
                } else {
                    PaywallView(displayCloseButton: true)
                }
            }
            .onPurchaseCompleted { customerInfo in
                AptabaseClient.shared.trackEvent(eventName: "pro_purchased")
                proManager.updateFromCustomerInfo(customerInfo)
                dismiss()
            }
            .onRestoreCompleted { customerInfo in
                proManager.updateFromCustomerInfo(customerInfo)
                if customerInfo.entitlements["pro"]?.isActive == true ||
                   customerInfo.entitlements["songflip_pro"]?.isActive == true {
                    dismiss()
                }
            }
            .onRequestedDismissal {
                dismiss()
            }
            .task {
                if !proManager.isPro {
                    proManager.trackPaywallImpression()
                }
            }
        }
    }
}
