import UIKit
import SwiftUI
import TesseraCoil

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let coilLoader = CoilImageLoader.companion.create()
        return MainViewControllerKt.MainViewController(imageLoader: coilLoader)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
