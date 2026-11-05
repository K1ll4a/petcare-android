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
        WindowGroup { PetCareRootView().ignoresSafeArea() }
    }
}
