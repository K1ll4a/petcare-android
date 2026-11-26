import SwiftUI
import PetCareShared

struct PetCareRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct PetCareApp: App {
    var body: some Scene {
        WindowGroup {
            PetCareRootView()
                .ignoresSafeArea()
                // Ссылка из Telegram: petcare://tasks/{task_id}?occurrence_id={id}.
                .onOpenURL { url in _ = MainViewControllerKt.handleDeepLink(url: url.absoluteString) }
        }
    }
}
