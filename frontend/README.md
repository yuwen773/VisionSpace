# VisionSpace

#### Description

VisionSpace is an enterprise-level intelligent collaborative cloud image management system, providing features like image upload, management, and sharing. It supports multi-theme switching (Deep Space Dark / Pop Art) with modern UI design.

#### Tech Stack

- **Frontend Framework**: Vue 3 + TypeScript
- **Build Tool**: Vite 6
- **UI Component Library**: Ant Design Vue 4
- **State Management**: Pinia
- **Router**: Vue Router 4
- **Charts**: ECharts + vue-echarts
- **Image Processing**: Vue-Cropper
- **Styling**: Less + CSS Variables

#### Software Architecture

```
src/
├── api/                 # API interfaces
├── assets/              # Static assets
├── components/          # Shared components
│   ├── GlobalHeader.vue # Global header
│   ├── PictureList.vue  # Picture list
│   ├── PictureUpload.vue # Image upload
│   ├── ShareModal.vue   # Share modal
│   └── ThemeToggleButton.vue # Theme toggle
├── composables/         # Composition APIs
│   └── useTheme.ts     # Theme management
├── constants/           # Constants
├── layouts/             # Layout components
├── pages/               # Page components
│   ├── user/           # User pages (login/register)
│   ├── admin/          # Admin dashboard
│   ├── AddPicturePage.vue    # Add picture
│   ├── AddSpacePage.vue      # Add space
│   ├── HomePage.vue          # Home page
│   ├── MySpacePage.vue       # My space
│   └── PictureDetailPage.vue  # Picture detail
├── router/              # Router configuration
├── stores/              # Pinia state management
├── styles/              # Global styles
│   ├── design-tokens.css     # Design tokens
│   ├── design-tokens-pop.css # Pop design tokens
│   ├── global.css            # Global styles
│   ├── global-pop.css        # Pop global styles
│   ├── antd-theme.css        # Ant Design Deep Space theme
│   └── antd-theme-pop.css    # Ant Design Pop theme
└── utils/               # Utilities
```

#### Features

- User Registration / Login
- Image Upload & Management
- Space Creation (Private / Team)
- Image Sharing
- Theme Switching (Deep Space Dark / Pop Art)
- Admin Dashboard (User, Picture, Space Management)

#### Installation

1. Install Node.js (>= 18.0.0)
2. Install dependencies
   \3. Start development server
   \4. Visit 
#### Build for Production

\
#### Contribution

1. Fork the repository
2. Create a Feature branch (\)
3. Commit your changes (\)
4. Push to the branch (\)
5. Create a Pull Request
