import SwiftUI

struct SettingsSheetView: View {
    @EnvironmentObject var settings: SettingsModel
    @Environment(\.dismiss) var dismiss
    @State private var showingLanguagePicker = false

    var lang: String { settings.selectedLanguage }

    var currentLanguageItem: LanguageOption {
        SettingsModel.supportedLanguages.first { $0.code == settings.selectedLanguage }
            ?? SettingsModel.supportedLanguages[0]
    }

    var body: some View {
        NavigationStack {
            List {
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
                }

                Section(header: Text(LocalizationManager.string(for: "settings_feedback_support", lang: lang)).font(.caption).fontWeight(.semibold)) {
                    Button(action: {
                        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
                        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
                        let systemVersion = UIDevice.current.systemVersion
                        let model = UIDevice.current.model
                        let subject = "SongFlip iOS Feedback (v\(appVersion))".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "SongFlip%20Feedback"
                        let body = "\n\n---\nApp Version: v\(appVersion) (\(buildNumber))\niOS: \(systemVersion)\nDevice: \(model)".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                        if let url = URL(string: "mailto:songflip@goork.de?subject=\(subject)&body=\(body)") {
                            UIApplication.shared.open(url)
                        }
                    }) {
                        HStack {
                            Label(LocalizationManager.string(for: "settings_feedback_support", lang: lang), systemImage: "envelope")
                            Spacer()
                            Image(systemName: "arrow.up.right")
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
            .preferredColorScheme(settings.colorScheme)
        }
    }
}
