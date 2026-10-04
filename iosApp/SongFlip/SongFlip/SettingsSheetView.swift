import SwiftUI
import SongFlipKit

struct SettingsSheetView: View {
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var proManager = ProManager.shared
    @Environment(\.dismiss) var dismiss
    @State private var showingLanguagePicker = false
    @State private var showingPaywallSheet = false
    @State private var showingFeedbackSheet = false

    var lang: String { settings.selectedLanguage }

    var currentLanguageItem: LanguageOption {
        SettingsModel.supportedLanguages.first { $0.code == settings.selectedLanguage }
            ?? SettingsModel.supportedLanguages[0]
    }

    private var proSubtitle: String {
        if proManager.isPro {
            if let type = proManager.proType {
                if type.contains("lifetime") {
                    return LocalizationManager.string(for: "pro_active_lifetime", lang: lang)
                } else if let date = proManager.expirationDate {
                    let formatter = DateFormatter()
                    formatter.dateStyle = .medium
                    let dateStr = formatter.string(from: date)
                    return String(format: LocalizationManager.string(for: "pro_active_annual", lang: lang), dateStr)
                }
            }
            return LocalizationManager.string(for: "pro_active_lifetime", lang: lang)
        } else {
            return LocalizationManager.string(for: "pro_upgrade_card_subtitle", lang: lang)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                // 0. SongFlip PRO Card
                Section {
                    Button(action: {
                        AptabaseClient.shared.trackEvent("paywall_viewed", properties: ["source": "settings_card"])
                        showingPaywallSheet = true
                    }) {
                        HStack(spacing: 14) {
                            Text("💎")
                                .font(.title2)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(proManager.isPro ? LocalizationManager.string(for: "pro_active_status", lang: lang) : LocalizationManager.string(for: "pro_title", lang: lang))
                                    .font(.headline)
                                    .foregroundColor(.primary)

                                Text(proSubtitle)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }

                Section(header: Text(LocalizationManager.string(for: "section_general", lang: lang)).font(.caption).fontWeight(.semibold)) {
                    Button(action: { showingLanguagePicker = true }) {
                        HStack {
                            Label(LocalizationManager.string(for: "language_label", lang: lang), systemImage: "globe")
                            Spacer()
                            Text("\(currentLanguageItem.flag) \(currentLanguageItem.name)")
                                .foregroundColor(.secondary)
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .foregroundColor(.primary)

                    VStack(alignment: .leading, spacing: 8) {
                        Label(LocalizationManager.string(for: "theme_label", lang: lang), systemImage: "circle.righthalf.filled")
                        Picker(LocalizationManager.string(for: "theme_label", lang: lang), selection: Binding(
                            get: { settings.themeMode },
                            set: { newMode in
                                settings.themeMode = newMode
                                dismiss()
                            }
                        )) {
                            Text(LocalizationManager.string(for: "theme_dark", lang: lang)).tag("dark")
                            Text(LocalizationManager.string(for: "theme_light", lang: lang)).tag("light")
                            Text(LocalizationManager.string(for: "theme_system", lang: lang)).tag("system")
                        }
                        .pickerStyle(SegmentedPickerStyle())
                    }
                    .padding(.vertical, 4)

                    Toggle(isOn: $settings.autoClipboardDetect) {
                        Label(LocalizationManager.string(for: "auto_clipboard", lang: lang), systemImage: "doc.on.clipboard")
                    }
                    .tint(.green)

                    Toggle(isOn: $settings.askEveryTime) {
                        Label(LocalizationManager.string(for: "settings_ask_every_time_title", lang: lang), systemImage: "arrow.triangle.branch")
                    }
                    .tint(.green)
                }

                Section(header: Text(LocalizationManager.string(for: "settings_feedback_support", lang: lang)).font(.caption).fontWeight(.semibold)) {
                    ShareLink(
                        item: LocalizationManager.string(for: "share_app_message", lang: lang)
                    ) {
                        HStack {
                            Label(LocalizationManager.string(for: "settings_share_app", lang: lang), systemImage: "square.and.arrow.up")
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .simultaneousGesture(TapGesture().onEnded {
                        AptabaseClient.shared.trackShareAppClicked()
                    })
                    .foregroundColor(.primary)

                    Button(action: {
                        showingFeedbackSheet = true
                    }) {
                        HStack {
                            Label(LocalizationManager.string(for: "settings_feedback_support", lang: lang), systemImage: "envelope")
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .foregroundColor(.primary)
                }
            }
            .navigationTitle(LocalizationManager.string(for: "nav_settings", lang: lang))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(LocalizationManager.string(for: "btn_done", lang: lang)) { dismiss() }
                        .fontWeight(.semibold)
                }
            }
            .sheet(isPresented: $showingLanguagePicker) {
                NavigationStack {
                    List(SettingsModel.supportedLanguages) { item in
                        Button(action: {
                            settings.selectedLanguage = item.code
                            showingLanguagePicker = false
                        }) {
                            HStack {
                                Text("\(item.flag)  \(item.name)")
                                    .foregroundColor(.primary)
                                Spacer()
                                if settings.selectedLanguage == item.code {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(.green)
                                        .fontWeight(.bold)
                                }
                            }
                        }
                    }
                    .navigationTitle(LocalizationManager.string(for: "select_language_title", lang: lang))
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button(LocalizationManager.string(for: "btn_close", lang: lang)) { showingLanguagePicker = false }
                        }
                    }
                    .preferredColorScheme(settings.colorScheme)
                }
            }
            .sheet(isPresented: $showingPaywallSheet) {
                ProPaywallSheetView()
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(isPresented: $showingFeedbackSheet) {
                FeedbackSheetView()
                    .preferredColorScheme(settings.colorScheme)
            }
            .preferredColorScheme(settings.colorScheme)
        }
    }
}
